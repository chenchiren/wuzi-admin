package com.relex.wuzi.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 列表 CSV 导出（带 BOM，Excel 可直接打开）。 */
public final class CsvExport {

    private CsvExport() {
    }

    public static ResponseEntity<byte[]> toResponse(String filename, List<String> headers, List<List<String>> rows) {
        String csv = toCsv(headers, rows);
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        String name = filename == null || filename.isBlank() ? "export.csv" : filename;
        if (!name.toLowerCase().endsWith(".csv")) {
            name = name + ".csv";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    public static String toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(CsvExport::escape).toList())).append("\r\n");
        if (rows != null) {
            for (List<String> cells : rows) {
                sb.append(String.join(",", cells.stream().map(CsvExport::escape).toList())).append("\r\n");
            }
        }
        return sb.toString();
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
