package pdav.tudor.domain.entropy;

public class AC {
    int runLength;
    int size;
    int amplitude;

    public AC(int runLength, int size, int amplitude) {
        this.runLength = runLength;
        this.size = size;
        this.amplitude = amplitude;
    }

    public void decrementRunLength() {
        this.runLength--;
    }

    public int getSize() {
        return size;
    }

    public int getRunLength() {
        return runLength;
    }

    public int getAmplitude() {
        return amplitude;
    }
}