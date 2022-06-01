package pdav.tudor;

import pdav.tudor.domain.Decoder;
import pdav.tudor.domain.Encoder;

import java.io.IOException;

public class Main {
    private static final String FILENAME = "C:\\Users\\Tudor\\Desktop\\D\\faculta\\SemV\\PDAV\\Lab_image_encoder_decoder\\nt-P3.ppm";
    private static final String RESULT_FILENAME = "C:\\Users\\Tudor\\Desktop\\D\\faculta\\SemV\\PDAV\\Lab_image_encoder_decoder\\result-nt-P3-task3.ppm";
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    public static void main(String[] args) {

        Encoder encoder = new Encoder(FILENAME);
        encoder.readPPMImage();
        encoder.convertRGBtoYUV();
        encoder.storeBlocks();
        encoder.forwardDCT();
        encoder.quantization();
        encoder.entropyEncoding();

        Decoder decoder = new Decoder(RESULT_FILENAME,
                encoder.getYBlocks(),
                encoder.getUBlocks(),
                encoder.getVBlocks(),
                WIDTH,
                HEIGHT);
        decoder.entropyDecoding(encoder.getEntropyList());
        decoder.deQuantization();
        decoder.inverseDCT();
        decoder.convertBlocksToMatrices();
        decoder.convertYUVtoRGB();
        try {
            decoder.writePPMImage();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
