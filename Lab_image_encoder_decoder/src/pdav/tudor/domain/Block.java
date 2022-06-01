package pdav.tudor.domain;


public class Block {
    private final int size, line, column;
    private final double[][] values;

    public Block(int size, int line, int column) {
        this.size = size;
        this.line = line;
        this.column = column;
        values = new double[size][size];
    }

    public void modifyValue(double value, int line, int column) {
        this.values[line][column] = value;
    }

    public void modifyValues(int[][] values) {
        for (int line = 0; line < size; line++) {
            for (int column = 0; column < size; column++) {
                modifyValue(values[line][column], line, column);
            }
        }
    }

    public double getValue(int line, int column) {
        return this.values[line][column];
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("Block{" +
                "size=" + size +
                ", line=" + line +
                ", column=" + column +
                ", values=[\n");
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                str.append(values[i][j]);
                str.append(',');
            }
            str.append('\n');
        }
        str.append(']');

        return str.toString();
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public double[][] getValues() {
        return values;
    }
}
