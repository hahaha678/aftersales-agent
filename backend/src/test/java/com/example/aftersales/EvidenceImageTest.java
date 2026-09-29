package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.aftersales.service.EvidenceImage;
import com.example.aftersales.common.exception.ApiRequestException;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class EvidenceImageTest {

    @Test
    void detectsActualFormatAndRemovesTrailingContent() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        output.write("<script>untrusted</script>".getBytes());
        var image = EvidenceImage.read(
            new MockMultipartFile("file", "../../fake.png", "image/png", output.toByteArray())
        );
        assertThat(image.mediaType()).isEqualTo("image/jpeg");
        assertThat(new String(image.content(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(
            "<script>"
        );
        assertThat(ImageIO.read(new ByteArrayInputStream(image.content())).getHeight()).isEqualTo(3);
    }

    @Test
    void rejectsExcessiveDimensionsBeforePixelDecode() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6001, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThatThrownBy(() -> EvidenceImage.read(new MockMultipartFile("file", output.toByteArray())))
            .isInstanceOf(ApiRequestException.class)
            .hasMessageContaining("6000");
    }
}
