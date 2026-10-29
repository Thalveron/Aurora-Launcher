package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;

/** Render public account-initialization state before the browser paints the login card. */
final class ConsoleLoginPage {
    private ConsoleLoginPage() {}

    static byte[] render(byte[] template, boolean initialized) {
        String html=new String(template,StandardCharsets.UTF_8);
        html=html.replace("__AURORA_INITIALIZED__",String.valueOf(initialized))
                .replace("<form id=\"login\" hidden","<form id=\"login\"")
                .replace("连接你的电视",initialized?"登录控制中心":"首次设置你的控制中心")
                .replace("进入控制中心 <span>↗</span>",initialized?"进入控制中心":"创建账号并进入");
        if(!initialized)html=html.replace("id=\"open-setup\" type=\"button\" class=\"login-secondary\" hidden","id=\"open-setup\" type=\"button\" class=\"login-secondary\"")
                .replace("<label id=\"confirm-row\" hidden>","<label id=\"confirm-row\">")
                .replace("autocomplete=\"current-password\"","autocomplete=\"new-password\"");
        return html.getBytes(StandardCharsets.UTF_8);
    }
}
