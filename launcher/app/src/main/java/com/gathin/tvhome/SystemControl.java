package com.gathin.tvhome;

/** Platform capabilities, independent of the theme and of a particular manufacturer. */
interface SystemControl {
    String name();
    boolean supports(String capability);
    String reason(String capability);
    void powerStandby() throws Exception;
    byte[] screenshot() throws Exception;
    void key(int code) throws Exception;
    void launch(String packageName) throws Exception;
}
