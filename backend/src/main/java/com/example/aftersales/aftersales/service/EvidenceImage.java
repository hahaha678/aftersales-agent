package com.example.aftersales.aftersales.service;

import com.example.aftersales.common.exception.ApiRequestException;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;

/** 不信任文件名或浏览器 MIME；先检查像素数量，再解码并重编码以移除附加内容。 */
public record EvidenceImage(byte[] content, String mediaType, String hash) {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public static EvidenceImage read(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw ApiRequestException.invalid(
            "图片不能为空且不能超过 5 MB"
        );
        try {
            byte[] source = file.getBytes();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw ApiRequestException.invalid("请选择有效的 JPEG 或 PNG 图片");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png")) throw ApiRequestException.invalid(
                        "仅支持 JPEG 和 PNG 图片"
                    );
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0),
                        height = reader.getHeight(0);
                    if (width > 6000 || height > 6000 || (long) width * height > 16000000) {
                        throw ApiRequestException.invalid("图片最大边长 6000 像素，总像素不能超过 1600 万");
                    }
                    var decoded = reader.read(0);
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(decoded, format, output)) throw ApiRequestException.invalid("图片无法处理");
                    byte[] clean = output.toByteArray();
                    if (clean.length > MAX_BYTES) throw ApiRequestException.invalid(
                        "处理后的图片超过 5 MB，请压缩后上传"
                    );
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
                    return new EvidenceImage(clean, "image/" + format, hash);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw ApiRequestException.invalid("图片损坏或无法读取");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
