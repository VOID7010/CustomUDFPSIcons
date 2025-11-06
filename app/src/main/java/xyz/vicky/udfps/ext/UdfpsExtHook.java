package xyz.vicky.udfps.ext;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XModuleResources;
import de.robv.android.xposed.XResources;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

/**
 * Replaces a drawable array in the target app (SystemUI by default)
 * with our module-defined array @array/udfps_icons.
 * Change TARGET_PKG / CANDIDATE_ARRAY_NAMES if your ROM uses different names.
 */
public class UdfpsExtHook implements IXposedHookZygoteInit, IXposedHookInitPackageResources {

    private static String sModulePath;

    private static final String TARGET_PKG = "com.android.systemui";
    private static final String[] CANDIDATE_ARRAY_NAMES = new String[] {
        "udfps_icons", "config_udfps_icons", "fod_icons", "fodicon_pack"
    };

    @Override
    public void initZygote(StartupParam startupParam) {
        sModulePath = startupParam.modulePath;
        XposedBridge.log("[UDFPS-EXT] initZygote OK, modulePath=" + sModulePath);
    }

    @Override
    public void handleInitPackageResources(final XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!TARGET_PKG.equals(resparam.packageName)) return;

        try {
            XModuleResources modRes = XModuleResources.createInstance(sModulePath, resparam.res);

            int myArrayId = modRes.getIdentifier("udfps_icons", "array", "xyz.vicky.udfps.ext");
            if (myArrayId == 0) {
                XposedBridge.log("[UDFPS-EXT] Missing @array/udfps_icons in module resources");
                return;
            }

            Object forwarder = modRes.fwd(myArrayId);

            boolean replaced = false;
            for (String name : CANDIDATE_ARRAY_NAMES) {
                int targetId = resparam.res.getIdentifier(name, "array", TARGET_PKG);
                if (targetId != 0) {
                    resparam.res.setReplacement(TARGET_PKG, "array", name, forwarder);
                    XposedBridge.log("[UDFPS-EXT] Forwarded array  + name +  to module @array/udfps_icons");
                    replaced = true;
                }
            }

            if (!replaced) {
                XposedBridge.log("[UDFPS-EXT] Couldn’t find a matching array in " + TARGET_PKG + " to replace");
            }
        } catch (Throwable t) {
            XposedBridge.log("[UDFPS-EXT] Error replacing resources: " + t);
            throw t;
        }
    }
}
