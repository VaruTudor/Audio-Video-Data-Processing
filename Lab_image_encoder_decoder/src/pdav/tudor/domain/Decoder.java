package pdav.tudor.domain;

import pdav.tudor.domain.entropy.Entropy;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class Decoder {
    private final static int BLOCK_SIZE = 8;
    private final String filename;
    private final int width;
    private final int height;
    private final int maxPixelColorValue;
    private final int minPixelColorValue;
    private int[][] r;
    private int[][] g;
    private int[][] b;
    private double[][] y;
    private double[][] u;
    private double[][] v;
    private final List<Block> yBlocks;
    private final List<Block> uBlocks;
    private final List<Block> vBlocks;

    public Decoder(String filename, List<Block> y, List<Block> u, List<Block> v, int width, int height) {
        this.filename = filename;
        this.minPixelColorValue = 0;
        this.maxPixelColorValue = 255;
        this.yBlocks = y;
        this.uBlocks = u;
        this.vBlocks = v;
        this.width = width;
        this.height = height;
    }

    public void convertBlocksToMatrices() {
        int heightDivided = height / BLOCK_SIZE;
        int widthDivided = width / BLOCK_SIZE;

        // initialize RGB arrays
        this.r = new int[height][width];
        this.g = new int[height][width];
        this.b = new int[height][width];

        // initialize YUV arrays
        this.y = new double[height][width];
        this.u = new double[height][width];
        this.v = new double[height][width];

        int line = 0;
        int column = 0;
        int currentBlock = 0;
        while (true) {
            if (column == widthDivided && line == heightDivided - 1) {
                break;
            } else if (column == widthDivided) {
                column = 0;
                line++;
            }
            Block yBlock = yBlocks.get(currentBlock);
            Block uBlock = uBlocks.get(currentBlock);
            Block vBlock = vBlocks.get(currentBlock);
            for (int blockLine = 0; blockLine < BLOCK_SIZE; blockLine++) {
                for (int blockColumn = 0; blockColumn < BLOCK_SIZE; blockColumn++) {
                    y[line * BLOCK_SIZE + blockLine][column * BLOCK_SIZE + blockColumn] = yBlock.getValue(blockLine, blockColumn);
                    u[line * BLOCK_SIZE + blockLine][column * BLOCK_SIZE + blockColumn] = uBlock.getValue(blockLine, blockColumn);
                    v[line * BLOCK_SIZE + blockLine][column * BLOCK_SIZE + blockColumn] = vBlock.getValue(blockLine, blockColumn);
                }
            }
            column++;
            currentBlock++;
        }
    }

    /**
     * R = Y + 1.140V
     * G = Y - 0.395U - 0.581V
     * B = Y + 2.032U
     * taken from: https://www.pcmag.com/encyclopedia/term/yuvrgb-conversion-formulas
     */
    public void convertYUVtoRGB() {
        for (int line = 0; line < height; line++)
            for (int column = 0; column < width; column++) {

                double rValue = y[line][column] + 1.140 * v[line][column];
                double gValue = y[line][column] - 0.395 * u[line][column] - 0.581 * v[line][column];
                double bValue = y[line][column] + 2.032 * u[line][column];

                if (rValue > this.maxPixelColorValue) rValue = this.maxPixelColorValue;
                if (gValue > this.maxPixelColorValue) gValue = this.maxPixelColorValue;
                if (bValue > this.maxPixelColorValue) bValue = this.maxPixelColorValue;

                if (rValue < this.minPixelColorValue) rValue = this.minPixelColorValue;
                if (gValue < this.minPixelColorValue) gValue = this.minPixelColorValue;
                if (bValue < this.minPixelColorValue) bValue = this.minPixelColorValue;

                this.r[line][column] = (int) rValue;
                this.g[line][column] = (int) gValue;
                this.b[line][column] = (int) bValue;
            }
    }

    private double alpha(int value) {
        return value > 0 ? 1 : (1 / Math.sqrt(2.0));
    }

    private double blockCosProduct(Block block, int u, int v) {
        double sum = 0;
        for (int x = 0; x < BLOCK_SIZE; x++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {
                // Before applying the Forward DCT, you should subtract 128 from each value of every 8x8 Y/Cb/Cr block
                sum += Math.cos(((2 * u + 1) * x * Math.PI) / 16) *
                        Math.cos(((2 * v + 1) * y * Math.PI) / 16) *
                        block.getValue(x, y) *
                        alpha(x) *
                        alpha(y);
            }
        }
        return sum;
    }

    public void inverseDCT() {
        Arrays.asList(yBlocks, uBlocks, vBlocks).forEach(
                blocks -> blocks.forEach(block -> {
                            // create a new block which will replace the previous
                            Block NonDCTBlock = new Block(BLOCK_SIZE, block.getLine(), block.getColumn());
                            for (int u = 0; u < BLOCK_SIZE; u++) {
                                for (int v = 0; v < BLOCK_SIZE; v++) {
                                    // apply the formula
                                    double Guv = 0.25 * blockCosProduct(block, u, v);
                                    // do not forget to add 128 to each value of every 8x8 Y/Cb/Cr block obtained
                                    NonDCTBlock.modifyValue(Guv + 128, u, v);
                                }
                            }
                            blocks.set(block.getLine() * this.width / BLOCK_SIZE + block.getColumn(), NonDCTBlock);
                        }
                )
        );
    }

    public void deQuantization() {
        Arrays.asList(yBlocks, uBlocks, vBlocks).forEach(
                blocks -> blocks.forEach(block -> {
                            for (int line = 0; line < BLOCK_SIZE; line++) {
                                for (int column = 0; column < BLOCK_SIZE; column++) {
                                    block.modifyValue(
                                            block.getValue(line, column) * QuantizationMatrix.values[line][column],
                                            line,
                                            column
                                    );
                                }
                            }
                        }
                )
        );
    }

    private int[][] zigZagMatrix(Entropy entropy) {
        int m, n;
        m = n = BLOCK_SIZE;
        int[][] result = new int[][]{
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}
        };
        int index = 0;

        int row = 0, col = 0;

        // Boolean variable that will true if we
        // need to increment 'row' value otherwise
        // false- if increment 'col' value
        boolean row_inc = false;

        // Print matrix of lower half zig-zag pattern
        int currentACIndex = 0;
        boolean zeroSequence = false;

        int mn = Math.min(m, n);
        for (int len = 1; len <= mn; ++len) {
            for (int i = 0; i < len; ++i) {
                if (row == 0 && col == 0){
                    result[0][0] = entropy.dc.getAmplitude();
                }
                else if (entropy.acList.get(currentACIndex).getSize() != 0 || entropy.acList.get(currentACIndex).getAmplitude() != 0)
                    if (entropy.acList.get(currentACIndex).getRunLength() > 0) {
                        zeroSequence = true;
                        entropy.acList.get(currentACIndex).decrementRunLength();
                    } else if (entropy.acList.get(currentACIndex).getRunLength() == 0 && zeroSequence) {
                        currentACIndex++;
                        zeroSequence = false;
                    } else {
                        result[row][col] = entropy.acList.get(currentACIndex).getAmplitude();
                        currentACIndex++;
                    }
                index++;

                if (i + 1 == len)
                    break;
                // If row_increment value is true
                // increment row and decrement col
                // else decrement row and increment
                // col
                if (row_inc) {
                    ++row;
                    --col;
                } else {
                    --row;
                    ++col;
                }
            }

            if (len == mn)
                break;

            // Update row or col value according
            // to the last increment
            if (row_inc) {
                ++row;
                row_inc = false;
            } else {
                ++col;
                row_inc = true;
            }
        }

        // Update the indexes of row and col variable
        if (row == 0) {
            if (col == m - 1)
                ++row;
            else
                ++col;
            row_inc = true;
        } else {
            if (row == n - 1)
                ++col;
            else
                ++row;
            row_inc = false;
        }

        // Print the next half zig-zag pattern
        int MAX = Math.max(m, n) - 1;
        for (int len, diag = MAX; diag > 0; --diag) {

            if (diag > mn)
                len = mn;
            else
                len = diag;

            for (int i = 0; i < len; ++i) {
                if (entropy.acList.get(currentACIndex).getSize() != 0 || entropy.acList.get(currentACIndex).getAmplitude() != 0)
                    if (entropy.acList.get(currentACIndex).getRunLength() > 0) {
                        zeroSequence = true;
                        entropy.acList.get(currentACIndex).decrementRunLength();
                    } else if (entropy.acList.get(currentACIndex).getRunLength() == 0 && zeroSequence) {
                        currentACIndex++;
                        zeroSequence = false;
                    } else {
                        result[row][col] = entropy.acList.get(currentACIndex).getAmplitude();
                        currentACIndex++;
                    }
                index++;

                if (i + 1 == len)
                    break;

                // Update row or col value according
                // to the last increment
                if (row_inc) {
                    ++row;
                    --col;
                } else {
                    ++col;
                    --row;
                }
            }

            // Update the indexes of row and col variable
            if (row == 0 || col == m - 1) {
                if (col == m - 1)
                    ++row;
                else
                    ++col;

                row_inc = true;
            } else if (col == 0 || row == n - 1) {
                if (row == n - 1)
                    ++col;
                else
                    ++row;

                row_inc = false;
            }
        }
        return result;
    }

    public void entropyDecoding(List<Entropy> entropyList) {
        for (int index = 0; index < entropyList.size(); index++) {
            if (index % 3 == 0) {
                yBlocks.get(index / 3).modifyValues(zigZagMatrix(entropyList.get(index)));
            } else if (index % 3 == 1) {
                uBlocks.get(index / 3).modifyValues(zigZagMatrix(entropyList.get(index)));
            } else {
                vBlocks.get(index / 3).modifyValues(zigZagMatrix(entropyList.get(index)));
            }
        }
    }

    public void writePPMImage() throws IOException {
        FileWriter fileWriter = new FileWriter(filename);
        PrintWriter printWriter = new PrintWriter(fileWriter);

        printWriter.println("P3");
        printWriter.println("800 600");
        printWriter.println("255");
        for (int line = 0; line < height; line++) {
            for (int column = 0; column < width; column++) {
                printWriter.println(this.r[line][column]);
                printWriter.println(this.g[line][column]);
                printWriter.println(this.b[line][column]);
            }
        }

        printWriter.close();
    }
}
