/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.math;

import com.fastasyncworldedit.core.math.MutableBlockVector3;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.sk89q.worldedit.math.BitMath.mask;
import static com.sk89q.worldedit.math.BitMath.unpackX;
import static com.sk89q.worldedit.math.BitMath.unpackY;
import static com.sk89q.worldedit.math.BitMath.unpackZ;

/**
 * An immutable 3-dimensional vector.
 */
//FAWE start - not a record
public abstract class BlockVector3 {
//FAWE end

    public static final BlockVector3 ZERO = BlockVector3.at(0, 0, 0);
    public static final BlockVector3 UNIT_X = BlockVector3.at(1, 0, 0);
    public static final BlockVector3 UNIT_Y = BlockVector3.at(0, 1, 0);
    public static final BlockVector3 UNIT_Z = BlockVector3.at(0, 0, 1);
    public static final BlockVector3 UNIT_MINUS_X = BlockVector3.at(-1, 0, 0);
    public static final BlockVector3 UNIT_MINUS_Y = BlockVector3.at(0, -1, 0);
    public static final BlockVector3 UNIT_MINUS_Z = BlockVector3.at(0, 0, -1);
    public static final BlockVector3 ONE = BlockVector3.at(1, 1, 1);

    public static BlockVector3 at(double x, double y, double z) {
        return at((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public static BlockVector3 at(int x, int y, int z) {
        /*FAWE start unnecessary
        // switch for efficiency on typical cases
        // in MC y is rarely 0/1 on selections
        switch (y) {
            case 0:
                if (x == 0 && z == 0) {
                    return ZERO;
                }
                break;
            case 1:
                if (x == 1 && z == 1) {
                    return ONE;
                }
                break;
            default:
                break;
        }
        */
        //FAWE end
        return new BlockVector3Imp(x, y, z);
    }

    private static final int WORLD_XZ_MINMAX = 30_000_000;
    private static final int WORLD_Y_MIN = -2048;
    private static final int WORLD_Y_MAX = 2047;

    private static boolean isHorizontallyInBounds(int h) {
        return -WORLD_XZ_MINMAX <= h && h <= WORLD_XZ_MINMAX;
    }

    public static boolean isLongPackable(BlockVector3 location) {
        return isHorizontallyInBounds(location.x())
                && isHorizontallyInBounds(location.z())
                && WORLD_Y_MIN <= location.y() && location.y() <= WORLD_Y_MAX;
    }

    public static void checkLongPackable(BlockVector3 location) {
        checkArgument(isLongPackable(location),
                "Location exceeds long packing limits: %s", location
        );
    }

    private static final long BITS_26 = mask(26);
    private static final long BITS_12 = mask(12);

    public static BlockVector3 fromLongPackedForm(long packed) {
        return at(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    // thread-safe initialization idiom
    private static final class YzxOrderComparator {

        private static final Comparator<BlockVector3> YZX_ORDER =
                Comparator.comparingInt(BlockVector3::y)
                        .thenComparingInt(BlockVector3::z)
                        .thenComparingInt(BlockVector3::x);

    }

    /**
     * Returns a comparator that sorts vectors first by Y, then Z, then X.
     *
     * <p>
     * Useful for sorting by chunk block storage order.
     * </p>
     */
    public static Comparator<BlockVector3> sortByCoordsYzx() {
        return YzxOrderComparator.YZX_ORDER;
    }

    //FAWE start
    public boolean isAt(int x, int y, int z) {
        return x() == x && y() == y && z() == z;
    }

    public MutableBlockVector3 setComponents(double x, double y, double z) {
        return new MutableBlockVector3((int) x, (int) y, (int) z);
    }

    public MutableBlockVector3 setComponents(int x, int y, int z) {
        return new MutableBlockVector3(x, y, z);
    }

    public long toLongPackedForm() {
        checkLongPackable(this);
        return (x() & BITS_26) | ((z() & BITS_26) << 26) | (((y() & BITS_12) << (26 + 26)));
    }

    public MutableBlockVector3 mutX(double x) {
        return new MutableBlockVector3((int) x, y(), z());
    }

    public MutableBlockVector3 mutY(double y) {
        return new MutableBlockVector3(x(), (int) y, z());
    }

    public MutableBlockVector3 mutZ(double z) {
        return new MutableBlockVector3(x(), y(), (int) z);
    }

    public MutableBlockVector3 mutX(int x) {
        return new MutableBlockVector3(x, y(), z());
    }

    public MutableBlockVector3 mutY(int y) {
        return new MutableBlockVector3(x(), y, z());
    }

    public MutableBlockVector3 mutZ(int z) {
        return new MutableBlockVector3(x(), y(), z);
    }

    public BlockVector3 toImmutable() {
        return BlockVector3.at(x(), y(), z());
    }
    //FAWE end

    //FAWE start - make record getters to abstract methods
    /**
     * Get the X coordinate.
     *
     * @return the x coordinate
     * @since 2.11.0
     */
    public abstract int x();
    //FAWE end

    /**
     * Get the X coordinate.
     *
     * @return the x coordinate
     * @deprecated use {@link #x()} instead
     */
    @Deprecated(forRemoval = true, since = "2.11.0")
    public int getX() {
        return this.x(); //FAWE - access abstract getter instead of local field
    }

    /**
     * Get the X coordinate.
     *
     * @return the x coordinate
     * @deprecated use {@link #x()} instead
     */
    @Deprecated(forRemoval = true, since = "2.11.0")
    public int getBlockX() {
        return this.x(); //FAWE - access abstract getter instead of local field
    }

    /**
     * Set the X coordinate.
     *
     * @param x the new X
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 withX(int x) {
        return BlockVector3.at(x, y(), z());
    }
    //FAWE end


    //FAWE start - make record getters to abstract methods
    /**
     * Get the Y coordinate.
     *
     * @return the y coordinate
     * @since 2.11.0
     */
    public abstract int y();
    //FAWE end

    /**
     * Get the Y coordinate.
     *
     * @return the y coordinate
     * @deprecated use {@link #y()} instead
     */
    @Deprecated(forRemoval = true, since = "2.11.0")
    public int getY() {
        return this.y(); //FAWE - access abstract getter instead of local field
    }

    /**
     * Get the Y coordinate.
     *
     * @return the y coordinate
     * @deprecated use {@link #y()} instead
     */
    @Deprecated(forRemoval = true, since = "2.11.0")
    public int getBlockY() {
        return this.y(); //FAWE - access abstract getter instead of local field
    }

    /**
     * Set the Y coordinate.
     *
     * @param y the new Y
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 withY(int y) {
        return BlockVector3.at(x(), y, z());
    }
    //FAWE end

    //FAWE start - make record getters to abstract methods
    /**
     * Get the Z coordinate.
     *
     * @return the Z coordinate
     * @since 2.11.0
     */
    public abstract int z();
    //FAWE end

    /**
     * Get the Z coordinate.
     *
     * @return the z coordinate
     * @deprecated use {@link #z()} instead
     */
    @Deprecated(forRemoval = true, since = "2.11.0")
    public int getZ() {
        return this.z(); //FAWE - access abstract getter instead of local field
    }

    /**
     * Get the Z coordinate.
     *
     * @return the z coordinate
     * @deprecated use {@link #z()} instead
     */
    @Deprecated(forRemoval = true, since = "2.11.0")
    public int getBlockZ() {
        return this.z(); //FAWE - access abstract getter instead of local field
    }

    /**
     * Set the Z coordinate.
     *
     * @param z the new Z
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 withZ(int z) {
        return BlockVector3.at(x(), y(), z);
    }
    //FAWE end

    /**
     * Add another vector to this vector and return the result as a new vector.
     *
     * @param other the other vector
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 add(BlockVector3 other) {
        return add(other.x(), other.y(), other.z());
    }
    //FAWE end

    /**
     * Add another vector to this vector and return the result as a new vector.
     *
     * @param x the value to add
     * @param y the value to add
     * @param z the value to add
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 add(int x, int y, int z) {
        return BlockVector3.at(this.x() + x, this.y() + y, this.z() + z);
    }
    //FAWE end

    /**
     * Add a list of vectors to this vector and return the
     * result as a new vector.
     *
     * @param others an array of vectors
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 add(BlockVector3... others) {
        int newX = x();
        int newY = y();
        int newZ = z();

        for (BlockVector3 other : others) {
            newX += other.x();
            newY += other.y();
            newZ += other.z();
        }

        return BlockVector3.at(newX, newY, newZ);
    }
    //FAWE end

    /**
     * Subtract another vector from this vector and return the result
     * as a new vector.
     *
     * @param other the other vector
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 subtract(BlockVector3 other) {
        return subtract(other.x(), other.y(), other.z());
    }
    //FAWE end

    /**
     * Subtract another vector from this vector and return the result
     * as a new vector.
     *
     * @param x the value to subtract
     * @param y the value to subtract
     * @param z the value to subtract
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 subtract(int x, int y, int z) {
        return BlockVector3.at(this.x() - x, this.y() - y, this.z() - z);
    }
    //FAWE end

    /**
     * Subtract a list of vectors from this vector and return the result
     * as a new vector.
     *
     * @param others an array of vectors
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 subtract(BlockVector3... others) {
        int newX = x();
        int newY = y();
        int newZ = z();

        for (BlockVector3 other : others) {
            newX -= other.x();
            newY -= other.y();
            newZ -= other.z();
        }

        return BlockVector3.at(newX, newY, newZ);
    }
    //FAWE end

    /**
     * Multiply this vector by another vector on each component.
     *
     * @param other the other vector
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 multiply(BlockVector3 other) {
        return multiply(other.x(), other.y(), other.z());
    }
    //FAWE end

    /**
     * Multiply this vector by another vector on each component.
     *
     * @param x the value to multiply
     * @param y the value to multiply
     * @param z the value to multiply
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 multiply(int x, int y, int z) {
        return BlockVector3.at(this.x() * x, this.y() * y, this.z() * z);
    }
    //FAWE end

    /**
     * Multiply this vector by zero or more vectors on each component.
     *
     * @param others an array of vectors
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 multiply(BlockVector3... others) {
        int newX = x();
        int newY = y();
        int newZ = z();

        for (BlockVector3 other : others) {
            newX *= other.x();
            newY *= other.y();
            newZ *= other.z();
        }

        return BlockVector3.at(newX, newY, newZ);
    }
    //FAWE end

    /**
     * Perform scalar multiplication and return a new vector.
     *
     * @param n the value to multiply
     * @return a new vector
     */
    public BlockVector3 multiply(int n) {
        return multiply(n, n, n);
    }

    /**
     * Divide this vector by another vector on each component.
     *
     * @param other the other vector
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 divide(BlockVector3 other) {
        return divide(other.x(), other.y(), other.z());
    }
    //FAWE end

    /**
     * Divide this vector by another vector on each component.
     *
     * @param x the value to divide by
     * @param y the value to divide by
     * @param z the value to divide by
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 divide(int x, int y, int z) {
        return BlockVector3.at(this.x() / x, this.y() / y, this.z() / z);
    }
    //FAWE end

    /**
     * Perform scalar division and return a new vector.
     *
     * @param n the value to divide by
     * @return a new vector
     */
    public BlockVector3 divide(int n) {
        return divide(n, n, n);
    }

    /**
     * Shift all components right.
     *
     * @param x the value to shift x by
     * @param y the value to shift y by
     * @param z the value to shift z by
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 shr(int x, int y, int z) {
        return at(this.x() >> x, this.y() >> y, this.z() >> z);
    }
    //FAWE end

    /**
     * Shift all components right by {@code n}.
     *
     * @param n the value to shift by
     * @return a new vector
     */
    public BlockVector3 shr(int n) {
        return shr(n, n, n);
    }

    /**
     * Shift all components left.
     *
     * @param x the value to shift x by
     * @param y the value to shift y by
     * @param z the value to shift z by
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 shl(int x, int y, int z) {
        return at(this.x() << x, this.y() << y, this.z() << z);
    }
    //FAWE end

    /**
     * Shift all components left by {@code n}.
     *
     * @param n the value to shift by
     * @return a new vector
     */
    public BlockVector3 shl(int n) {
        return shl(n, n, n);
    }

    /**
     * Get the length of the vector.
     *
     * @return length
     */
    public double length() {
        return Math.sqrt(lengthSq());
    }

    /**
     * Get the length, squared, of the vector.
     *
     * @return length, squared
     */
    //FAWE start - getter
    public int lengthSq() {
        return x() * x() + y() * y() + z() * z();
    }
    //FAWE end

    /**
     * Get the distance between this vector and another vector.
     *
     * @param other the other vector
     * @return distance
     */
    public double distance(BlockVector3 other) {
        return Math.sqrt(distanceSq(other));
    }

    /**
     * Get the distance between this vector and another vector, squared.
     *
     * @param other the other vector
     * @return distance
     */
    //FAWE start - getter
    public int distanceSq(BlockVector3 other) {
        int dx = other.x() - x();
        int dy = other.y() - y();
        int dz = other.z() - z();
        return dx * dx + dy * dy + dz * dz;
    }
    //FAWE end

    /**
     * Get the normalized vector, which is the vector divided by its
     * length, as a new vector.
     *
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 normalize() {
        double len = length();
        double x = this.x() / len;
        double y = this.y() / len;
        double z = this.z() / len;
        return BlockVector3.at(x, y, z);
    }
    //FAWE end

    /**
     * Gets the dot product of this and another vector.
     *
     * @param other the other vector
     * @return the dot product of this and the other vector
     */
    //FAWE start - getter
    public double dot(BlockVector3 other) {
        return x() * other.x() + y() * other.y() + z() * other.z();
    }
    //FAWE end

    /**
     * Gets the cross product of this and another vector.
     *
     * @param other the other vector
     * @return the cross product of this and the other vector
     */
    //FAWE start - getter
    public BlockVector3 cross(BlockVector3 other) {
        return new BlockVector3Imp(
                y() * other.z() - z() * other.y(),
                z() * other.x() - x() * other.z(),
                x() * other.y() - y() * other.x()
        );
    }
    //FAWE end

    /**
     * Checks to see if a vector is contained with another.
     *
     * @param min the minimum point (X, Y, and Z are the lowest)
     * @param max the maximum point (X, Y, and Z are the lowest)
     * @return true if the vector is contained
     */
    //FAWE start - getter
    public boolean containedWithin(BlockVector3 min, BlockVector3 max) {
        return x() >= min.x() && x() <= max.x() && y() >= min.y() && y() <= max.y() && z() >= min.z() && z() <= max.z();
    }
    //FAWE end

    /**
     * Clamp the Y component.
     *
     * @param min the minimum value
     * @param max the maximum value
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 clampY(int min, int max) {
        checkArgument(min <= max, "minimum cannot be greater than maximum");
        if (y() < min) {
            return BlockVector3.at(x(), min, z());
        }
        if (y() > max) {
            return BlockVector3.at(x(), max, z());
        }
        return this;
    }
    //FAWE end

    /**
     * Floors the values of all components.
     *
     * @return a new vector
     */
    public BlockVector3 floor() {
        // already floored, kept for feature parity with Vector3
        return this;
    }

    /**
     * Rounds all components up.
     *
     * @return a new vector
     */
    public BlockVector3 ceil() {
        // already raised, kept for feature parity with Vector3
        return this;
    }

    /**
     * Rounds all components to the closest integer.
     *
     * <p>Components &lt; 0.5 are rounded down, otherwise up.</p>
     *
     * @return a new vector
     */
    public BlockVector3 round() {
        // already rounded, kept for feature parity with Vector3
        return this;
    }

    /**
     * Returns a vector with the absolute values of the components of
     * this vector.
     *
     * @return a new vector
     */
    //FAWE start - getter
    public BlockVector3 abs() {
        return BlockVector3.at(Math.abs(x()), Math.abs(y()), Math.abs(z()));
    }
    //FAWE end

    /**
     * Perform a 2D transformation on this vector and return a new one.
     *
     * @param angle      in degrees
     * @param aboutX     about which x coordinate to rotate
     * @param aboutZ     about which z coordinate to rotate
     * @param translateX what to add after rotation
     * @param translateZ what to add after rotation
     * @return a new vector
     * @see AffineTransform another method to transform vectors
     */
    //FAWE start - getter
    public BlockVector3 transform2D(double angle, double aboutX, double aboutZ, double translateX, double translateZ) {
        angle = Math.toRadians(angle);
        double x = this.x() - aboutX;
        double z = this.z() - aboutZ;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x2 = x * cos - z * sin;
        double z2 = x * sin + z * cos;

        return BlockVector3.at(
                x2 + aboutX + translateX,
                y(),
                z2 + aboutZ + translateZ
        );
    }
    //FAWE end

    /**
     * Get this vector's pitch as used within the game.
     *
     * @return pitch in radians
     */
    public double toPitch() {
        double x = x();
        double z = z();

        if (x == 0 && z == 0) {
            return y() > 0 ? -90 : 90;
        } else {
            double x2 = x * x;
            double z2 = z * z;
            double xz = Math.sqrt(x2 + z2);
            return Math.toDegrees(Math.atan(-y() / xz));
        }
    }

    /**
     * Get this vector's yaw as used within the game.
     *
     * @return yaw in radians
     */
    public double toYaw() {
        double x = x();
        double z = z();

        double t = Math.atan2(-x, z);
        double tau = 2 * Math.PI;

        return Math.toDegrees(((t + tau) % tau));
    }

    /**
     * Gets the minimum components of two vectors.
     *
     * @param v2 the second vector
     * @return minimum
     */
    //FAWE start - getter
    public BlockVector3 getMinimum(BlockVector3 v2) {
        return new BlockVector3Imp(
                Math.min(x(), v2.x()),
                Math.min(y(), v2.y()),
                Math.min(z(), v2.z())
        );
    }
    //FAWE end

    /**
     * Gets the maximum components of two vectors.
     *
     * @param v2 the second vector
     * @return maximum
     */
    //FAWE start - getter
    public BlockVector3 getMaximum(BlockVector3 v2) {
        return new BlockVector3Imp(
                Math.max(x(), v2.x()),
                Math.max(y(), v2.y()),
                Math.max(z(), v2.z())
        );
    }
    //FAWE end

    //FAWE start
    /*
    Methods for getting/setting blocks

    Why are these methods here?
        - Getting a block at a position requires various operations
            (bounds checks, cache checks, ensuring loaded chunk, get ChunkSection, etc.)
        - When iterating over a region, it will provide custom BlockVector3 positions
        - These override the below set/get and avoid lookups (as the iterator shifts it to the chunk level)
     */

    public boolean setOrdinal(Extent orDefault, int ordinal) {
        return orDefault.setBlock(this, BlockState.getFromOrdinal(ordinal));
    }

    public boolean setBlock(Extent orDefault, BlockState state) {
        return orDefault.setBlock(this, state);
    }

    public boolean setFullBlock(Extent orDefault, BaseBlock block) {
        return orDefault.setBlock(this, block);
    }

    public int getOrdinal(Extent orDefault) {
        return getBlock(orDefault).getOrdinal();
    }

    @Deprecated
    public char getOrdinalChar(Extent orDefault) {
        return orDefault.getBlock(this).getOrdinalChar();
    }

    public BlockState getBlock(Extent orDefault) {
        return orDefault.getBlock(this);
    }

    public BaseBlock getFullBlock(Extent orDefault) {
        return orDefault.getFullBlock(this);
    }

    public boolean setBiome(Extent orDefault, BiomeType type) {
        return orDefault.setBiome(this, type);
    }

    public BiomeType getBiome(Extent orDefault) {
        return orDefault.getBiome(this);
    }

    @Deprecated(forRemoval = true, since = "2.11.2")
    public CompoundTag getNbtData(Extent orDefault) {
        return orDefault.getFullBlock(x(), y(), z()).getNbtData();
    }

    public BlockState getOrdinalBelow(Extent orDefault) {
        return orDefault.getBlock(x(), y() - 1, z());
    }

    public BlockState getStateAbove(Extent orDefault) {
        return orDefault.getBlock(x(), y() + 1, z());
    }

    public BlockState getStateRelativeY(Extent orDefault, final int y) {
        return orDefault.getBlock(x(), y() + y, z());
    }

    /**
     * Creates a 2D vector by dropping the Y component from this vector.
     *
     * @return a new {@link BlockVector2}
     */
    public BlockVector2 toBlockVector2() {
        return BlockVector2.at(x(), z());
    }

    public Vector3 toVector3() {
        return Vector3.at(x(), y(), z());
    }

    //FAWE start - not a record, need own implementations
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof final BlockVector3 other)) {
            return false;
        }

        return other.x() == this.x() && other.y() == this.y() && other.z() == this.z();
    }

    public final boolean equals(BlockVector3 other) {
        if (other == null) {
            return false;
        }

        return other.x() == this.x() && other.y() == this.y() && other.z() == this.z();
    }

    @Override
    public int hashCode() {
        return (x() ^ (z() << 12)) ^ (y() << 24);
    }
    //FAWE end

    @Override
    public String toString() {
        return "(" + x() + ", " + y() + ", " + z() + ")";
    }

    /**
     * Returns a string representation that is supported by the parser.
     *
     * @return string
     */
    public String toParserString() {
        return x() + "," + y() + "," + z();
    }

    //Used by VS fork
    public BlockVector3 plus(BlockVector3 other) {
        return add(other);
    }
    //FAWE end

}
