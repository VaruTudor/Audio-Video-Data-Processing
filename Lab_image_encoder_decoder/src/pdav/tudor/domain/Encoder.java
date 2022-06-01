package pdav.tudor.domain;

import pdav.tudor.domain.entropy.AC;
import pdav.tudor.domain.entropy.DC;
import pdav.tudor.domain.entropy.Entropy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Encoder {
    private final static int BLOCK_SIZE = 8;
    private final String filename;
    private int width;
    private int height;
    private int[][] r;
    private int[][] g;
    private int[][] b;
    private double[][] y;
    private double[][] u;
    private double[][] v;
    private List<Block> yBlocks;
    private List<Block> uBlocks;
    private List<Block> vBlocks;
    private List<Entropy> entropyList;

    public Encoder(String filename) {
        this.filename = filename;
    }

    private BufferedReader openFile() {
        try {
            return new BufferedReader(new FileReader(this.filename));
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    /**
     * Reading a PPM file assuming it is in P3 format.
     * The first line contains the format, the next line is a comment and the third line gives
     * the width and the height of the image. The following lines will store the RGB values (in batches of 3 lines)
     * of each pixel starting from the top-left.
     */
    public void readPPMImage() {
        BufferedReader bufferedReader = openFile();
        if (bufferedReader != null) {
            try {
                if (!bufferedReader.readLine().equals("P3")) {
                    throw new RuntimeException("Format not supported");
                }

                bufferedReader.readLine();
                String[] sizeInfo = bufferedReader.readLine().split(" ");
                this.width = Integer.parseInt(sizeInfo[0]);
                this.height = Integer.parseInt(sizeInfo[1]);
                bufferedReader.readLine();

                // initialize RGB arrays
                this.r = new int[height][width];
                this.g = new int[height][width];
                this.b = new int[height][width];

                // initialize YUV arrays
                this.y = new double[height][width];
                this.u = new double[height][width];
                this.v = new double[height][width];

                String firstLineOfBatch;
                int line = 0;
                int column = 0;
                while ((firstLineOfBatch = bufferedReader.readLine()) != null) {
                    if (column == width) {
                        column = 0;
                        line++;
                    }

                    this.r[line][column] = Integer.parseInt(firstLineOfBatch);
                    this.g[line][column] = Integer.parseInt(bufferedReader.readLine());
                    this.b[line][column] = Integer.parseInt(bufferedReader.readLine());
                    column++;
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Y =  0.299R + 0.587G + 0.114B
     * U = -0.147R - 0.289G + 0.436B
     * V =  0.615R - 0.515G - 0.100B
     * taken from: https://www.pcmag.com/encyclopedia/term/yuvrgb-conversion-formulas
     */
    public void convertRGBtoYUV() {
        for (int line = 0; line < height; line++)
            for (int column = 0; column < width; column++) {
                y[line][column] = 0.299 * r[line][column] + 0.587 * g[line][column] + 0.114 * b[line][column];
                u[line][column] = -0.147 * r[line][column] - 0.289 * g[line][column] + 0.436 * b[line][column];
                v[line][column] = 0.615 * r[line][column] - 0.515 * g[line][column] - 0.100 * b[line][column];
            }
    }

    /**
     * perform 4:2:0 subsampling
     */
    private void subsampling(Block block) {
        int sampleSize = 2;
        int sizeDivided = BLOCK_SIZE / sampleSize;

        for (int blockLine = 0; blockLine < sizeDivided; blockLine++) {
            for (int blockColumn = 0; blockColumn < sizeDivided; blockColumn++) {
                double sum = 0;
                int sizeOfSample = 0;
                for (int line = blockLine * sampleSize; line < (blockLine + 1) * sampleSize; line++)
                    for (int column = blockColumn * sampleSize; column < (blockColumn + 1) * sampleSize; column++) {
                        sum += block.getValue(line, column);
                        sizeOfSample++;
                    }
                double average = sum / sizeOfSample;
                for (int line = blockLine * sampleSize; line < (blockLine + 1) * sampleSize; line++)
                    for (int column = blockColumn * sampleSize; column < (blockColumn + 1) * sampleSize; column++) {
                        block.modifyValue(average, line, column);
                    }
            }
        }
    }

    public List<Block> divideIntoBlocks(char type) {
        List<Block> blockList = new ArrayList<>();
        int heightDivided = height / BLOCK_SIZE;
        int widthDivided = width / BLOCK_SIZE;
        double[][] currentMatrix = y;

        if (type == 'U') {
            currentMatrix = u;
        } else if (type == 'V') {
            currentMatrix = v;
        }

        for (int blockLine = 0; blockLine < heightDivided; blockLine++) {
            for (int blockColumn = 0; blockColumn < widthDivided; blockColumn++) {
                Block block = new Block(BLOCK_SIZE, blockLine, blockColumn);
                int currentBlockLine = 0;
                int currentBlockColumn = 0;
                for (int line = blockLine * BLOCK_SIZE; line < (blockLine + 1) * BLOCK_SIZE; line++) {
                    for (int column = blockColumn * BLOCK_SIZE; column < (blockColumn + 1) * BLOCK_SIZE; column++) {
                        block.modifyValue((int) currentMatrix[line][column], currentBlockLine, currentBlockColumn);
                        currentBlockColumn++;
                    }
                    currentBlockColumn = 0;
                    currentBlockLine++;
                }
                if (type == 'U' || type == 'V') {
                    subsampling(block);
                }
                blockList.add(block);
            }
        }
        return blockList;
    }

    public void storeBlocks() {
        yBlocks = divideIntoBlocks('Y');
        uBlocks = divideIntoBlocks('U');
        vBlocks = divideIntoBlocks('V');
    }

    private double alpha(int value) {
        return value > 0 ? 1 : (1 / Math.sqrt(2.0));
    }

    private double blockCosProduct(Block block, int u, int v) {
        double sum = 0;
        for (int x = 0; x < BLOCK_SIZE; x++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {
                // Before applying the Forward DCT, you should subtract 128 from each value of every 8x8 Y/Cb/Cr block
                sum += Math.cos(((2 * x + 1) * u * Math.PI) / 16) *
                        Math.cos(((2 * y + 1) * v * Math.PI) / 16) *
                        (block.getValue(x, y) - 128);
            }
        }
        return sum;
    }

    public void forwardDCT() {
        Arrays.asList(yBlocks, uBlocks, vBlocks).forEach(
                blocks -> blocks.forEach(block -> {
                            // create a new block which will replace the previous
                            Block DCTBlock = new Block(BLOCK_SIZE, block.getLine(), block.getColumn());
                            for (int u = 0; u < BLOCK_SIZE; u++) {
                                for (int v = 0; v < BLOCK_SIZE; v++) {
                                    // apply the formula
                                    double Guv = 0.25 * alpha(u) * alpha(v) * blockCosProduct(block, u, v);
                                    DCTBlock.modifyValue(Guv, u, v);
                                }
                            }
                            // set the Forward DCT block to the previous
                            blocks.set(block.getLine() * this.width / BLOCK_SIZE + block.getColumn(), DCTBlock);
                        }
                )
        );
    }

    public void quantization() {
        Arrays.asList(yBlocks, uBlocks, vBlocks).forEach(
                blocks -> blocks.forEach(block -> {
                            for (int line = 0; line < BLOCK_SIZE; line++) {
                                for (int column = 0; column < BLOCK_SIZE; column++) {
                                    block.modifyValue(
                                            (int) (block.getValue(line, column) / QuantizationMatrix.values[line][column]),
                                            line,
                                            column
                                    );
                                }
                            }
                        }
                )
        );
    }

    private int[] zigZagMatrix(double[][] arr) {
        int m, n;
        m = n = BLOCK_SIZE;
        int[] result = new int[BLOCK_SIZE * BLOCK_SIZE];
        int index = 0;

        int row = 0, col = 0;

        // Boolean variable that will true if we
        // need to increment 'row' value otherwise
        // false- if increment 'col' value
        boolean row_inc = false;

        // Print matrix of lower half zig-zag pattern
        int mn = Math.min(m, n);
        for (int len = 1; len <= mn; ++len) {
            for (int i = 0; i < len; ++i) {
                result[index] = (int) arr[row][col];
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
                result[index] = (int) arr[row][col];
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

    private Entropy encodeBlock(Block block) {
        int[] matrix = zigZagMatrix(block.getValues());
        DC dc = new DC(
                AmplitudeMap.getCorrespondingSize(matrix[0]),
                matrix[0]
        );

        int currentRunLength = 0;
        List<AC> acList = new ArrayList<>();
        for (int index = 1; index < matrix.length; index++) {
            if (matrix[index] == 0) currentRunLength++;
            else {
                acList.add(
                        new AC(currentRunLength, AmplitudeMap.getCorrespondingSize(matrix[index]), matrix[index])
                );
                currentRunLength = 0;
            }
        }
        if (currentRunLength > 0) acList.add(new AC(currentRunLength, 0, 0));

        return new Entropy(dc, acList);
    }

    public void entropyEncoding() {
        this.entropyList = new ArrayList<>();

        for (int index = 0; index < yBlocks.size(); index++) {
            entropyList.add(encodeBlock(yBlocks.get(index)));
            entropyList.add(encodeBlock(uBlocks.get(index)));
            entropyList.add(encodeBlock(vBlocks.get(index)));
        }
    }


    public List<Block> getYBlocks() {
        return yBlocks;
    }

    public List<Block> getUBlocks() {
        return uBlocks;
    }

    public List<Block> getVBlocks() {
        return vBlocks;
    }

    public List<Entropy> getEntropyList() {
        return entropyList;
    }
}
