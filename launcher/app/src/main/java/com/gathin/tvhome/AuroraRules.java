package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deliberately small, independently testable security boundary for local themes and LAN control. */
final class AuroraRules {
    static boolean lan(InetAddress address) {
        return address.isLoopbackAddress() || address.isSiteLocalAddress()
                || ((address.getAddress()[0] & 0xfe) == 0xfc && address.getAddress().length == 16);
    }
    static boolean originAllowed(String origin, String host) {
        if (origin == null) return true; // Non-browser clients still need authentication.
        try {
            URI uri = new URI(origin);
            return "http".equals(uri.getScheme()) && host != null
                    && host.equalsIgnoreCase(uri.getRawAuthority()) && uri.getRawUserInfo() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
        } catch (Exception invalid) { return false; }
    }
    static int key(String name) {
        switch (name) {
            case "up": return 19; case "down": return 20;
            case "left": return 21; case "right": return 22;
            case "volume_up": return 24; case "volume_down": return 25;
            case "ok": return 23; case "back": return 4; case "home": return 3;
            default: throw new IllegalArgumentException("只允许方向键、确定、返回、主页和音量键");
        }
    }
    static File inside(File root, String relative) throws IOException {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\") || relative.indexOf('\0') >= 0)
            throw new IOException("文件路径无效");
        File target = new File(root, relative).getCanonicalFile();
        if (!target.getPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("禁止访问目录以外的文件");
        return target;
    }
    static String token() {
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static final class Confirmations {
        private final Map<String, Ticket> tickets = new LinkedHashMap<>();
        private static final class Ticket {
            final String owner; final long expires;
            Ticket(String owner, long expires) { this.owner=owner; this.expires=expires; }
        }
        synchronized String issue(String owner, long now) {
            tickets.entrySet().removeIf(e -> e.getValue().expires < now);
            if (tickets.size() >= 16) tickets.remove(tickets.keySet().iterator().next());
            String token=token(); tickets.put(token,new Ticket(owner,now+60000)); return token;
        }
        synchronized boolean consume(String token, String owner, long now) {
            Ticket ticket=tickets.get(token);
            if (ticket==null || !ticket.owner.equals(owner)) return false;
            tickets.remove(token);
            return ticket.expires >= now;
        }
    }
}
