package xyz.vicky.udfps.ext;

import android.content.res.XModuleResources;
import android.content.res.XResources;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

/**
 * Replace the UDFPS icon array from either SystemUI or the separate resources
 * package with our module-defined @array/udfps_icons.
 *
 * Scope in LSPosed: ONLY com.android.systemui is required.
 */
public class UdfpsExtHook implements IXposedHookZygoteInit, IXposedHookInitPackageResources {

    private static String sModulePath;

    // Known packages that might hold the array
    private static final String[] TARGET_PKGS = new String[] {
        "com.android.systemui",
        "org.evolution.udfps.icons"   // from your manifest dump
    };

    // Common array names ROMs use
    private static final String[] CANDIDATE_ARRAY_NAMES = new String[] {
        "udfps_icons", "config_udfps_icons", "fod_icons", "fodicon_pack",
        "udfps_icon_resources", "udfps_icons_array", "config_udfpsIconResources"
    };

    @Override
    public void initZygote(StartupParam startupParam) {
        sModulePath = startupParam.modulePath;
        try {
            // Prepare our resources once
            XModuleResources modRes = XModuleResources.createInstance(sModulePath, null);
            int myArrayId = modRes.getIdentifier("udfps_icons", "array", "xyz.vicky.udfps.ext");
            if (myArrayId == 0) {
                XposedBridge.log("[UDFPS-EXT] Missing @array/udfps_icons in module resources");
                return;
            }
            Object forwarder = modRes.fwd(myArrayId);

            // System-wide replacement so we don't need to hook every process
            for (String pkg : TARGET_PKGS) {
                for (String name : CANDIDATE_ARRAY_NAMES) {
                    try {
                        XResources.setSystemWideReplacement(pkg, "array", name, forwarder);
                        XposedBridge.log("[UDFPS-EXT] SystemWideReplacement " + pkg + ":array/" + name);
                    } catch (Throwable ignored) {
                        // Not present in this pkg; move on
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[UDFPS-EXT] initZygote error: " + t);
        }
    }

    @Override
    public void handleInitPackageResources(final XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        // Also try per-package replacement when SystemUI loads resources (extra safety)
        boolean target = false;
        for (String pkg : TARGET_PKGS) if (pkg.equals(resparam.packageName)) { target = true; break; }
        String lower = resparam.packageName.toLowerCase();
        if (!target && (lower.contains("udfps") || lower.contains("fod"))) return;

        try {
            XModuleResources modRes = XModuleResources.createInstance(sModulePath, resparam.res);
            int myArrayId = modRes.getIdentifier("udfps_icons", "array", "xyz.vicky.udfps.ext");
            if (myArrayId == 0) return;

            Object forwarder = modRes.fwd(myArrayId);
            boolean replaced = false;
            for (String name : CANDIDATE_ARRAY_NAMES) {
                int id = resparam.res.getIdentifier(name, "array", resparam.packageName);
                if (id != 0) {
                    resparam.res.setReplacement(resparam.packageName, "array", name, forwarder);
                    XposedBridge.log("[UDFPS-EXT] Forwarded " + resparam.packageName + ":array/" + name);
                    replaced = true;
                }
            }
            if (!replaced) {
                XposedBridge.log("[UDFPS-EXT] No matching array in " + resparam.packageName);
            }
        } catch (Throwable t) {
            XposedBridge.log("[UDFPS-EXT] handleInitPackageResources error: " + t);
            throw t;
        }
    }
}
