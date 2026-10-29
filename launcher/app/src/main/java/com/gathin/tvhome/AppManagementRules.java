package com.gathin.tvhome;

/** Pure validation shared by the application-management boundary and its tests. */
final class AppManagementRules {
    static String name(String value){
        String name=value.trim();
        if(name.codePointCount(0,name.length())>60||name.matches("(?s).*[\\p{Cntrl}\\u202a-\\u202e\\u2066-\\u2069].*"))throw new IllegalArgumentException("名称最多60字，不能含控制字符");
        return name;
    }
    static boolean protectedPackage(String pkg){
        return pkg.equals("android")||pkg.matches(".*(settings|systemui|permissioncontroller|packageinstaller|networkstack|providers|inputmethod|keyguard|bluetooth|telecom).*");
    }
}
