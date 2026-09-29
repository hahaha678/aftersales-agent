package com.example.aftersales.knowledge.service;

import java.util.*;

/** 小规模知识库使用可核对的原文窗口，保留重叠以减少切分处信息丢失。 */
public final class PolicyText {

    private PolicyText() {}

    public static List<String> split(String text) {
        int[] points = text.replace("\r\n", "\n").strip().codePoints().toArray();
        List<String> result = new ArrayList<>();
        for (int start = 0; start < points.length; start += 520) {
            int count = Math.min(600, points.length - start);
            result.add(new String(points, start, count));
            if (start + count == points.length) break;
        }
        return result;
    }

    public static double cosine(double[] a, double[] b) {
        if (a.length == 0 || a.length != b.length) throw new IllegalArgumentException("向量维度不一致");
        double dot = 0,
            aa = 0,
            bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        if (aa == 0 || bb == 0) return 0;
        return dot / Math.sqrt(aa * bb);
    }
}
