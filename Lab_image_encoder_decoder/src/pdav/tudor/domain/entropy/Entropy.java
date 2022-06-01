package pdav.tudor.domain.entropy;

import java.util.List;

public class Entropy {
    public final DC dc;
    public final List<AC> acList;

    public Entropy(DC dc, List<AC> acList) {
        this.dc = dc;
        this.acList = acList;
    }

}
