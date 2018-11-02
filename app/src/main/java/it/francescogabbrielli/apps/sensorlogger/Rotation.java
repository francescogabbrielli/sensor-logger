package it.francescogabbrielli.apps.sensorlogger;

public class Rotation {

    private int a11, a12, a13, a21, a22, a23, a31, a32, a33;

    private final static Rotation[] X_ROTATIONS = new Rotation[] {
            new Rotation(1, 0, 0, 0, 1, 0, 0, 0, 1),
            new Rotation(0,-1,0,1,0,0,0,0,1),
            new Rotation(-1,0,0,0,-1,0,0,0,1),
            new Rotation(0,1,0,-1,0,0,0,0,1),
    };
    private final static Rotation[] Y_ROTATIONS = new Rotation[] {
            new Rotation(1,0,0,0,1,0,0,0,1),
            new Rotation(0,0,1,0,1,0,-1,0,0),
            new Rotation(-1,0,0,0,1,0,0,0,-1),
            new Rotation(0,0,-1,0,1,0,1,0,0),
    };
    private final static Rotation[] Z_ROTATIONS = new Rotation[]{
            new Rotation(1,0,0,0,1,0,0,0,1),
            new Rotation(1,0,0,0,0,-1,0,1,0),
            new Rotation(1,0,0,0,-1,0,0,0,-1),
            new Rotation(1,0,0,0,0,1,0,-1,0),
    };

    public static Rotation getRotation(int x, int y, int z) {
        return Z_ROTATIONS[z].multiply(Y_ROTATIONS[y]).multiply(X_ROTATIONS[x]);
    }

    public Rotation(int a11, int a12, int a13,int a21, int a22, int a23, int a31, int a32, int a33) {
        this.a11 = a11;
        this.a12 = a12;
        this.a13 = a13;
        this.a21 = a21;
        this.a22 = a22;
        this.a23 = a23;
        this.a31 = a31;
        this.a32 = a32;
        this.a33 = a33;
    }

    public float[] multiply(float[] values) {
        float[] result = new float[values.length];
        result[0] = a11*values[0]+a12*values[1]+a13*values[2];
        result[1] = a21*values[0]+a22*values[1]+a23*values[2];
        result[2] = a31*values[0]+a32*values[1]+a33*values[2];
        return result;
    }
    
    private Rotation multiply(Rotation r) {
        return new Rotation(
            a11*r.a11+a12*r.a21+a13*r.a31,
            a11*r.a12+a12*r.a22+a13*r.a32,
            a11*r.a13+a12*r.a23+a13*r.a33,
            a21*r.a11+a22*r.a21+a23*r.a31,
            a21*r.a12+a22*r.a22+a23*r.a32,
            a21*r.a13+a22*r.a23+a23*r.a33,
            a31*r.a11+a32*r.a21+a33*r.a31,
            a31*r.a12+a32*r.a22+a33*r.a32,
            a31*r.a13+a32*r.a23+a33*r.a33
        );
    }


}
