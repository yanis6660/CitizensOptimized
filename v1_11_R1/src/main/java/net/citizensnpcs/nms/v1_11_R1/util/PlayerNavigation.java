package net.citizensnpcs.nms.v1_11_R1.util;

import net.citizensnpcs.nms.v1_11_R1.entity.EntityHumanNPC;
import net.minecraft.server.v1_11_R1.AttributeInstance;
import net.minecraft.server.v1_11_R1.AxisAlignedBB;
import net.minecraft.server.v1_11_R1.Block;
import net.minecraft.server.v1_11_R1.BlockPosition;
import net.minecraft.server.v1_11_R1.Blocks;
import net.minecraft.server.v1_11_R1.ChunkCache;
import net.minecraft.server.v1_11_R1.Entity;
import net.minecraft.server.v1_11_R1.EntityInsentient;
import net.minecraft.server.v1_11_R1.GenericAttributes;
import net.minecraft.server.v1_11_R1.IBlockData;
import net.minecraft.server.v1_11_R1.Material;
import net.minecraft.server.v1_11_R1.MathHelper;
import net.minecraft.server.v1_11_R1.NavigationAbstract;
import net.minecraft.server.v1_11_R1.PathEntity;
import net.minecraft.server.v1_11_R1.PathPoint;
import net.minecraft.server.v1_11_R1.PathType;
import net.minecraft.server.v1_11_R1.Pathfinder;
import net.minecraft.server.v1_11_R1.PathfinderAbstract;
import net.minecraft.server.v1_11_R1.Vec3D;
import net.minecraft.server.v1_11_R1.World;

public class PlayerNavigation extends NavigationAbstract {
    protected EntityHumanNPC a;
    protected World b;
    protected PathEntity c;
    protected double d;
    protected PlayerPathfinderNormal e;
    private final AttributeInstance f;
    private boolean f2;
    private int g;
    private int h;
    private Vec3D i = Vec3D.a;
    private Vec3D j = Vec3D.a;
    private long k;
    private long l;
    private double m;
    private float n = 0.5F;
    private boolean o;
    private long p;
    private BlockPosition q;
    private final PlayerPathfinder r;

    public PlayerNavigation(EntityHumanNPC entityinsentient, World world) {
        super(getDummyInsentient(entityinsentient, world), world);
        this.a = entityinsentient;
        this.b = world;
        this.f = entityinsentient.getAttributeInstance(GenericAttributes.FOLLOW_RANGE);
        this.f.setValue(24);
        this.e = new PlayerPathfinderNormal();
        this.e.a(true);
        this.r = new PlayerPathfinder(this.e);
        // this.b.C().a(this);
    }

    @Override
    protected Pathfinder a() {
        return null;
    }

    @Override
    public PathEntity a(BlockPosition paramBlockPosition) {
        BlockPosition localBlockPosition;
        if (this.b.getType(paramBlockPosition).getMaterial() == Material.AIR) {
            localBlockPosition = paramBlockPosition.down();
            while ((localBlockPosition.getY() > 0)
                    && (this.b.getType(localBlockPosition).getMaterial() == Material.AIR)) {
                localBlockPosition = localBlockPosition.down();
            }
            if (localBlockPosition.getY() > 0) {
                return a2(localBlockPosition.up());
            }
            while ((localBlockPosition.getY() < this.b.getHeight())
                    && (this.b.getType(localBlockPosition).getMaterial() == Material.AIR)) {
                localBlockPosition = localBlockPosition.up();
            }
            paramBlockPosition = localBlockPosition;
        }
        if (this.b.getType(paramBlockPosition).getMaterial().isBuildable()) {
            localBlockPosition = paramBlockPosition.up();
            while ((localBlockPosition.getY() < this.b.getHeight())
                    && (this.b.getType(localBlockPosition).getMaterial().isBuildable())) {
                localBlockPosition = localBlockPosition.up();
            }
            return a2(localBlockPosition);
        }
        return a2(paramBlockPosition);
    }

    public void a(boolean paramBoolean) {
        this.e.b(paramBoolean);
    }

    @Override
    public void a(double paramDouble) {
        this.d = paramDouble;
    }

    @Override
    public boolean a(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4) {
        return a(a(paramDouble1, paramDouble2, paramDouble3), paramDouble4);
    }

    @Override
    public PathEntity a(Entity paramEntity) {
        return a(new BlockPosition(paramEntity));
    }

    @Override
    public boolean a(Entity paramEntity, double paramDouble) {
        PathEntity localPathEntity = a(paramEntity);
        return (localPathEntity != null) && (a(localPathEntity, paramDouble));
    }

    private boolean a(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6,
            Vec3D paramVec3D, double paramDouble1, double paramDouble2) {
        int i = paramInt1 - paramInt4 / 2;
        int j = paramInt3 - paramInt6 / 2;
        if (!b(i, paramInt2, j, paramInt4, paramInt5, paramInt6, paramVec3D, paramDouble1, paramDouble2)) {
            return false;
        }
        for (int k = i; k < i + paramInt4; k++) {
            for (int m = j; m < j + paramInt6; m++) {
                double d1 = k + 0.5D - paramVec3D.x;
                double d2 = m + 0.5D - paramVec3D.z;
                if (d1 * paramDouble1 + d2 * paramDouble2 >= 0.0D) {
                    PathType localPathType = this.e.a(this.b, k, paramInt2 - 1, m, this.a, paramInt4, paramInt5,
                            paramInt6, true, true);
                    if ((localPathType == PathType.WATER) || (localPathType == PathType.LAVA)
                            || (localPathType == PathType.OPEN)) {
                        return false;
                    }
                    localPathType = this.e.a(this.b, k, paramInt2, m, this.a, paramInt4, paramInt5, paramInt6, true,
                            true);
                    float f1 = this.a.a(localPathType);
                    if ((f1 < 0.0F) || (f1 >= 8.0F)) {
                        return false;
                    }
                    if ((localPathType == PathType.DAMAGE_FIRE) || (localPathType == PathType.DANGER_FIRE)
                            || (localPathType == PathType.DAMAGE_OTHER)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean a(PathEntity paramPathEntity, double paramDouble) {
        if (paramPathEntity == null) {
            this.c = null;
            return false;
        }
        if (!paramPathEntity.a(this.c)) {
            this.c = paramPathEntity;
        }
        d();
        if (this.c.d() == 0) {
            return false;
        }
        this.d = paramDouble;
        Vec3D localVec3D = c();
        this.h = this.g;
        this.i = localVec3D;
        return true;
    }

    @Override
    protected void a(Vec3D paramVec3D) {
        if (this.g - this.h > 100) {
            if (paramVec3D.distanceSquared(this.i) < 2.25D) {
                o();
            }
            this.h = this.g;
            this.i = paramVec3D;
        }
        if ((this.c != null) && (!this.c.b())) {
            Vec3D localVec3D = this.c.f();
            if (localVec3D.equals(this.j)) {
                this.k += System.currentTimeMillis() - this.l;
            } else {
                this.j = localVec3D;
                double d1 = paramVec3D.f(this.j);
                this.m = (this.a.cq() > 0.0F ? d1 / this.a.cq() * 1000.0D : 0.0D);
            }
            if ((this.m > 0.0D) && (this.k > this.m * 3.0D)) {
                this.j = Vec3D.a;
                this.k = 0L;
                this.m = 0.0D;
                o();
            }
            this.l = System.currentTimeMillis();
        }
    }

    @Override
    protected boolean a(Vec3D paramVec3D1, Vec3D paramVec3D2, int paramInt1, int paramInt2, int paramInt3) {
        int i = MathHelper.floor(paramVec3D1.x);
        int j = MathHelper.floor(paramVec3D1.z);

        double d1 = paramVec3D2.x - paramVec3D1.x;
        double d2 = paramVec3D2.z - paramVec3D1.z;
        double d3 = d1 * d1 + d2 * d2;
        if (d3 < 1.0E-8D) {
            return false;
        }
        double d4 = 1.0D / Math.sqrt(d3);
        d1 *= d4;
        d2 *= d4;

        paramInt1 += 2;
        paramInt3 += 2;
        if (!a(i, (int) paramVec3D1.y, j, paramInt1, paramInt2, paramInt3, paramVec3D1, d1, d2)) {
            return false;
        }
        paramInt1 -= 2;
        paramInt3 -= 2;

        double d5 = 1.0D / Math.abs(d1);
        double d6 = 1.0D / Math.abs(d2);

        double d7 = i - paramVec3D1.x;
        double d8 = j - paramVec3D1.z;
        if (d1 >= 0.0D) {
            d7 += 1.0D;
        }
        if (d2 >= 0.0D) {
            d8 += 1.0D;
        }
        d7 /= d1;
        d8 /= d2;

        int k = d1 < 0.0D ? -1 : 1;
        int m = d2 < 0.0D ? -1 : 1;
        int n = MathHelper.floor(paramVec3D2.x);
        int i1 = MathHelper.floor(paramVec3D2.z);
        int i2 = n - i;
        int i3 = i1 - j;
        while ((i2 * k > 0) || (i3 * m > 0)) {
            if (d7 < d8) {
                d7 += d5;
                i += k;
                i2 = n - i;
            } else {
                d8 += d6;
                j += m;
                i3 = i1 - j;
            }
            if (!a(i, (int) paramVec3D1.y, j, paramInt1, paramInt2, paramInt3, paramVec3D1, d1, d2)) {
                return false;
            }
        }
        return true;
    }

    public PathEntity a2(BlockPosition paramBlockPosition) {
        if (!b()) {
            return null;
        }
        if ((this.c != null) && (!this.c.b()) && (paramBlockPosition.equals(this.q))) {
            return this.c;
        }
        this.q = paramBlockPosition;

        float f1 = h();
        this.b.methodProfiler.a("pathfind");
        BlockPosition localBlockPosition = new BlockPosition(this.a);
        int i1 = (int) (f1 + 8.0F);

        ChunkCache localChunkCache = new ChunkCache(this.b, localBlockPosition.a(-i1, -i1, -i1),
                localBlockPosition.a(i1, i1, i1), 0);
        PathEntity localPathEntity = this.r.a(localChunkCache, this.a, this.q, f1);
        this.b.methodProfiler.b();
        return localPathEntity;
    }

    public PathEntity a2(Entity paramEntity) {
        if (!b()) {
            return null;
        }
        BlockPosition localBlockPosition1 = new BlockPosition(paramEntity);
        if ((this.c != null) && (!this.c.b()) && (localBlockPosition1.equals(this.q))) {
            return this.c;
        }
        this.q = localBlockPosition1;

        float f1 = h();
        this.b.methodProfiler.a("pathfind");
        BlockPosition localBlockPosition2 = new BlockPosition(this.a).up();
        int i1 = (int) (f1 + 16.0F);

        ChunkCache localChunkCache = new ChunkCache(this.b, localBlockPosition2.a(-i1, -i1, -i1),
                localBlockPosition2.a(i1, i1, i1), 0);
        PathEntity localPathEntity = this.r.a(localChunkCache, this.a, paramEntity, f1);
        this.b.methodProfiler.b();
        return localPathEntity;
    }

    @Override
    protected boolean b() {
        return (this.a.onGround) || ((g()) && (p())) || (this.a.isPassenger());
    }

    @Override
    public boolean b(BlockPosition paramBlockPosition) {
        return this.b.getType(paramBlockPosition.down()).b();
    }

    public void b(boolean paramBoolean) {
        this.e.a(paramBoolean);
    }

    private boolean b(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6,
            Vec3D paramVec3D, double paramDouble1, double paramDouble2) {
        for (BlockPosition localBlockPosition : BlockPosition.a(new BlockPosition(paramInt1, paramInt2, paramInt3),
                new BlockPosition(paramInt1 + paramInt4 - 1, paramInt2 + paramInt5 - 1, paramInt3 + paramInt6 - 1))) {
            double d1 = localBlockPosition.getX() + 0.5D - paramVec3D.x;
            double d2 = localBlockPosition.getZ() + 0.5D - paramVec3D.z;
            if (d1 * paramDouble1 + d2 * paramDouble2 >= 0.0D) {
                Block localBlock = this.b.getType(localBlockPosition).getBlock();
                if (!localBlock.b(this.b, localBlockPosition)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected Vec3D c() {
        return new Vec3D(this.a.locX, r(), this.a.locZ);
    }

    public void c(boolean paramBoolean) {
        this.e.c(paramBoolean);
    }

    @Override
    protected void d() {
        PathPoint localPathPoint;
        for (int i = 0; i < this.c.d(); i++) {
            localPathPoint = this.c.a(i);
            Object localObject = i + 1 < this.c.d() ? this.c.a(i + 1) : null;

            IBlockData localIBlockData = this.b
                    .getType(new BlockPosition(localPathPoint.a, localPathPoint.b, localPathPoint.c));
            Block localBlock = localIBlockData.getBlock();
            if (localBlock == Blocks.cauldron) {
                this.c.a(i, localPathPoint.a(localPathPoint.a, localPathPoint.b + 1, localPathPoint.c));
                if ((localObject != null) && (localPathPoint.b >= ((PathPoint) localObject).b)) {
                    this.c.a(i + 1, ((PathPoint) localObject).a(((PathPoint) localObject).a, localPathPoint.b + 1,
                            ((PathPoint) localObject).c));
                }
            }
        }
        if (this.f2) {
            if (this.b.h(new BlockPosition(MathHelper.floor(this.a.locX), (int) (this.a.getBoundingBox().b + 0.5D),
                    MathHelper.floor(this.a.locZ)))) {
                return;
            }
            for (int i = 0; i < this.c.d(); i++) {
                localPathPoint = this.c.a(i);
                if (this.b.h(new BlockPosition(localPathPoint.a, localPathPoint.b, localPathPoint.c))) {
                    this.c.b(i - 1);
                    return;
                }
            }
        }
    }

    public void d(boolean paramBoolean) {
        this.f2 = paramBoolean;
    }

    public boolean f() {
        return this.e.c();
    }

    public boolean g() {
        return this.e.e();
    }

    @Override
    public float h() {
        return (float) this.f.getValue();
    }

    @Override
    public boolean i() {
        return this.o;
    }

    @Override
    public void j() {
        if (this.b.getTime() - this.p > 20L) {
            if (this.q != null) {
                this.c = null;
                this.c = a(this.q);
                this.p = this.b.getTime();
                this.o = false;
            }
        } else {
            this.o = true;
        }
    }

    @Override
    public PathEntity k() {
        return this.c;
    }

    @Override
    public void l() {
        this.g += 1;
        if (this.o) {
            j();
        }
        if (n()) {
            return;
        }
        if (b()) {
            m();
        } else if ((this.c != null) && (this.c.e() < this.c.d())) {
            Vec3D localVec3D = c();
            Vec3D localObject = this.c.a(this.a, this.c.e());
            if ((localVec3D.y > localObject.y) && (!this.a.onGround)
                    && (MathHelper.floor(localVec3D.x) == MathHelper.floor(localObject.x))
                    && (MathHelper.floor(localVec3D.z) == MathHelper.floor(localObject.z))) {
                this.c.c(this.c.e() + 1);
            }
        }
        if (n()) {
            return;
        }
        Vec3D localVec3D = this.c.a(this.a);
        if (localVec3D == null) {
            return;
        }
        Object localObject = new BlockPosition(localVec3D).down();
        AxisAlignedBB localAxisAlignedBB = this.b.getType((BlockPosition) localObject).d(this.b,
                (BlockPosition) localObject);
        localVec3D = localVec3D.a(0.0D, 1.0D - localAxisAlignedBB.e, 0.0D);

        this.a.getControllerMove().a(localVec3D.x, localVec3D.y, localVec3D.z, this.d);
    }

    @Override
    protected void m() {
        Vec3D localVec3D1 = c();

        int i1 = this.c.d();
        for (int i2 = this.c.e(); i2 < this.c.d(); i2++) {
            if (this.c.a(i2).b != Math.floor(localVec3D1.y)) {
                i1 = i2;
                break;
            }
        }
        this.n = (this.a.width > 0.75F ? this.a.width / 2.0F : 0.75F - this.a.width / 2.0F);
        Vec3D localVec3D2 = this.c.f();
        if ((MathHelper.e((float) (this.a.locX - (localVec3D2.x + 0.5D))) < this.n)
                && (MathHelper.e((float) (this.a.locZ - (localVec3D2.z + 0.5D))) < this.n)
                && (Math.abs(this.a.locY - localVec3D2.y) < 1.0D)) {
            this.c.c(this.c.e() + 1);
        }
        int i3 = MathHelper.f(this.a.width);
        int i4 = MathHelper.f(this.a.length);
        int i5 = i3;
        for (int i6 = i1 - 1; i6 >= this.c.e(); i6--) {
            if (a(localVec3D1, this.c.a(this.a, i6), i3, i4, i5)) {
                this.c.c(i6);
                break;
            }
        }
        a(localVec3D1);
    }

    @Override
    public boolean n() {
        return (this.c == null) || (this.c.b());
    }

    @Override
    public void o() {
        this.c = null;
    }

    @Override
    protected boolean p() {
        return (this.a.isInWater()) || (this.a.ao());
    }

    @Override
    public PathfinderAbstract q() {
        return this.e;
    }

    private int r() {
        if ((!this.a.isInWater()) || (!g())) {
            return (int) (this.a.getBoundingBox().b + 0.5D);
        }
        int i = (int) this.a.getBoundingBox().b;
        Block localBlock = this.b
                .getType(new BlockPosition(MathHelper.floor(this.a.locX), i, MathHelper.floor(this.a.locZ))).getBlock();
        int j = 0;
        while ((localBlock == Blocks.FLOWING_WATER) || (localBlock == Blocks.WATER)) {
            i++;
            localBlock = this.b
                    .getType(new BlockPosition(MathHelper.floor(this.a.locX), i, MathHelper.floor(this.a.locZ)))
                    .getBlock();
            j++;
            if (j > 16) {
                return (int) this.a.getBoundingBox().b;
            }
        }
        return i;
    }

    public void setRange(float pathfindingRange) {
        this.f.setValue(pathfindingRange);
    }

    private static EntityInsentient getDummyInsentient(EntityHumanNPC from, World world) {
        return new EntityInsentient(world) {
        };
    }
}