package com.relex.wuzi.service.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SMTP 邮件通知（简单 SMTP 客户端，无额外依赖）。
 * 配置 wuzi.mail.*：host/port/username/password/from/enabled/smock。
 * 未启用或 mock 时只记日志，发送失败不抛出、不阻断业务。
 */
@Slf4j
@Service
public class MailNotifyService {

    @Value("${wuzi.mail.enabled:false}")
    private boolean enabled;
    @Value("${wuzi.mail.host:}")
    private String host;
    @Value("${wuzi.mail.port:25}")
    private int port;
    @Value("${wuzi.mail.username:}")
    private String username;
    @Value("${wuzi.mail.password:}")
    private String password;
    @Value("${wuzi.mail.from:noreply@local}")
    private String from;
    /** mock 只记日志不真正连接 SMTP */
    @Value("${wuzi.mail.mock:true}")
    private boolean mock;

    public boolean isEnabled() {
        return enabled && !isMockMode();
    }

    public boolean isMockMode() {
        return mock || host == null || host.isBlank();
    }

    /**
     * 发送纯文本邮件。失败仅记日志。
     *
     * @return true 表示已尝试发送（含 mock 日志），false 表示未启用
     */
    public boolean send(String to, String subject, String content) {
        if (!enabled) {
            log.debug("[mail] 未启用，跳过 to={} subject={}", to, subject);
            return false;
        }
        if (to == null || to.isBlank()) {
            log.warn("[mail] 收件人为空，跳过 subject={}", subject);
            return false;
        }
        if (isMockMode()) {
            log.info("[mail:mock] from={} to={} subject={} content={}", from, to, subject, content);
            return true;
        }
        try {
            doSend(to, subject, content == null ? "" : content);
            log.info("[mail] 发送成功 to={} subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.warn("[mail] 发送失败 to={} subject={} err={}", to, subject, e.getMessage());
            return false;
        }
    }

    /** 简单 SMTP：EHLO → AUTH LOGIN → MAIL FROM → RCPT TO → DATA → QUIT */
    private void doSend(String to, String subject, String content) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 8000);
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            readReply(in, 220);
            write(out, "EHLO wuzi-admin");
            readReply(in, 250);
            if (username != null && !username.isBlank()) {
                write(out, "AUTH LOGIN");
                readReply(in, 334);
                write(out, b64(username));
                readReply(in, 334);
                write(out, b64(password == null ? "" : password));
                readReply(in, 235);
            }
            write(out, "MAIL FROM:<" + (from == null || from.isBlank() ? username : from) + ">");
            readReply(in, 250);
            write(out, "RCPT TO:<" + to + ">");
            readReply(in, 250);
            write(out, "DATA");
            readReply(in, 354);
            String data = "From: " + (from == null || from.isBlank() ? username : from) + "\r\n"
                    + "To: " + to + "\r\n"
                    + "Subject: " + encodeSubject(subject) + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: 8bit\r\n"
                    + "\r\n"
                    + content.replace("\r\n.", "\r\n..") + "\r\n.";
            write(out, data);
            readReply(in, 250);
            write(out, "QUIT");
        }
    }

    private void write(OutputStream out, String line) throws Exception {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void readReply(BufferedReader in, int expect) throws Exception {
        String line = in.readLine();
        if (line == null) {
            throw new IllegalStateException("SMTP 连接被关闭");
        }
        // 多行响应 250-xxx，等到最后一行 250 xxx
        while (line.length() >= 4 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) {
                throw new IllegalStateException("SMTP 响应不完整");
            }
        }
        int code = Integer.parseInt(line.trim().substring(0, 3));
        if (code != expect) {
            throw new IllegalStateException("SMTP 响应异常: " + line);
        }
    }

    private String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeSubject(String subject) {
        if (subject == null) {
            return "";
        }
        // 简单 RFC2047，避免非 ASCII 主题乱码
        return "=?UTF-8?B?" + b64(subject) + "?=";
    }
}
