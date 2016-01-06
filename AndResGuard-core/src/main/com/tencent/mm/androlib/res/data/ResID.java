package main.com.tencent.mm.androlib.res.data;


/**
 * @author shwenzhang
 */
public class ResID {
    public final int package_;
    public final int type;
    public final int entry;

    public final int id;

    public ResID(int package_, int type, int entry) {
        this(package_, type, entry, (package_ << 24) + (type << 16) + entry);
    }

    public ResID(int id) {
        this(id >> 24, (id >> 16) & 0x000000ff, id & 0x0000ffff, id);
    }

    public ResID(int package_, int type, int entry, int id) {
        this.package_ = package_;
        this.type = type;
        this.entry = entry;
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("0x%08x", id);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + this.id;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ResID other = (ResID) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
}
