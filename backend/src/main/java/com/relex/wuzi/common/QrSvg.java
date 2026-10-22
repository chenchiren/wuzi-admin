package com.relex.wuzi.common;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** 二维码 SVG 渲染（打印标签 / 页面内嵌，无外部依赖）。 */
public final class QrSvg {

    private QrSvg() {
    }

    /**
     * 将内容编码为可扫描的 QR SVG。
     *
     * @param content  码内容（UTF-8）
     * @param sizeCss  输出 CSS 尺寸，如 "22mm" / "120"
     */
    public static String encode(String content, String sizeCss) {
        if (content == null || content.isBlank()) {
            content = "EMPTY";
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
            return toSvg(matrix, sizeCss);
        } catch (WriterException e) {
            throw new IllegalStateException("二维码生成失败: " + e.getMessage(), e);
        }
    }

    private static String toSvg(BitMatrix matrix, String sizeCss) {
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        StringBuilder d = new StringBuilder(w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (matrix.get(x, y)) {
                    d.append('M').append(x).append(',').append(y).append("h1v1h-1z");
                }
            }
        }
        String css = (sizeCss == null || sizeCss.isBlank()) ? "100%" : sizeCss;
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + css + "\" height=\"" + css
                + "\" viewBox=\"0 0 " + w + " " + h + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + d + "\"/>"
                + "</svg>";
    }
}
