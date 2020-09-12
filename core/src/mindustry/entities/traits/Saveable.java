package mindustry.entities.traits;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface Saveable{
    void writeSave(DataOutput stream) throws IOException;
    void readSave(DataInput stream, byte version) throws IOException;
}
