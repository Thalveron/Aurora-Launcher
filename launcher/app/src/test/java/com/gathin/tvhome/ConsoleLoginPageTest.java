package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConsoleLoginPageTest {
    private final String template="<form id=\"login\" hidden data-initialized=\"__AURORA_INITIALIZED__\"><h2>连接你的电视</h2>"
            +"<input autocomplete=\"current-password\"><label id=\"confirm-row\" hidden>再次输入密码</label>"
            +"<button>进入控制中心 <span>↗</span></button></form>";

    @Test public void existingAccountIsVisibleOnFirstPaint() {
        String html=new String(ConsoleLoginPage.render(template.getBytes(StandardCharsets.UTF_8),true),StandardCharsets.UTF_8);
        assertTrue(html.contains("data-initialized=\"true\""));
        assertTrue(html.contains("登录控制中心"));
        assertFalse(html.contains("<form id=\"login\" hidden"));
        assertTrue(html.contains("id=\"confirm-row\" hidden"));
        assertFalse(html.contains("__AURORA_"));
    }

    @Test public void freshAccountShowsConfirmationBeforeScriptsLoad() {
        String html=new String(ConsoleLoginPage.render(template.getBytes(StandardCharsets.UTF_8),false),StandardCharsets.UTF_8);
        assertTrue(html.contains("data-initialized=\"false\""));
        assertTrue(html.contains("首次设置你的控制中心"));
        assertTrue(html.contains("创建账号并进入"));
        assertFalse(html.contains("id=\"confirm-row\" hidden"));
        assertTrue(html.contains("autocomplete=\"new-password\""));
    }
}
