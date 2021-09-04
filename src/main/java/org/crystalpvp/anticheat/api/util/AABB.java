package org.crystalpvp.anticheat.api.util;

import org.crystalpvp.anticheat.CrystalAC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AABB implements Cloneable {

    private Vector min;
    private Vector max;

    public static final AABB playerCollisionBox = new AABB(new Vector(-0.3, 0, -0.3), new Vector(0.3, 1.8, 0.3));
    public static final AABB playerWaterCollisionBox = new AABB(new Vector(-0.299, 0.401, -0.299), new Vector(0.299, 1.399, 0.299));
    public static final AABB playerLavaCollisionBox = new AABB(new Vector(-0.2, 0.4, -0.2), new Vector(0.2, 1.4, 0.2));

    public AABB(Vector min, Vector max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Calculates intersection with the given ray between a certain distance
     * interval.
     * <p>
     * Ray-box intersection is using IEEE numerical properties to ensure the
     * test is both robust and efficient, as described in:
     * <p>
     * Amy Williams, Steve Barrus, R. Keith Morley, and Peter Shirley: "An
     * Efficient and Robust Ray-Box Intersection Algorithm" Journal of graphics
     * tools, 10(1):49-54, 2005
     *
     * @param ray     incident ray
     * @param minDist minimum distance
     * @param maxDist maximum distance
     * @return intersection point on the bounding box (only the first is
     * returned) or null if no intersection
     */
    public Vector intersectsRay(Ray ray, float minDist, float maxDist) {
        Vector invDir = new Vector(1f / ray.getDirection().getX(), 1f / ray.getDirection().getY(), 1f / ray.getDirection().getZ());

        boolean signDirX = invDir.getX() < 0;
        boolean signDirY = invDir.getY() < 0;
        boolean signDirZ = invDir.getZ() < 0;

        Vector bbox = signDirX ? max : min;
        double tmin = (bbox.getX() - ray.getOrigin().getX()) * invDir.getX();
        bbox = signDirX ? min : max;
        double tmax = (bbox.getX() - ray.getOrigin().getX()) * invDir.getX();
        bbox = signDirY ? max : min;
        double tymin = (bbox.getY() - ray.getOrigin().getY()) * invDir.getY();
        bbox = signDirY ? min : max;
        double tymax = (bbox.getY() - ray.getOrigin().getY()) * invDir.getY();

        if ((tmin > tymax) || (tymin > tmax)) {
            return null;
        }
        if (tymin > tmin) {
            tmin = tymin;
        }
        if (tymax < tmax) {
            tmax = tymax;
        }

        bbox = signDirZ ? max : min;
        double tzmin = (bbox.getZ() - ray.getOrigin().getZ()) * invDir.getZ();
        bbox = signDirZ ? min : max;
        double tzmax = (bbox.getZ() - ray.getOrigin().getZ()) * invDir.getZ();

        if ((tmin > tzmax) || (tzmin > tmax)) {
            return null;
        }
        if (tzmin > tmin) {
            tmin = tzmin;
        }
        if (tzmax < tmax) {
            tmax = tzmax;
        }
        if ((tmin < maxDist) && (tmax > minDist)) {
            return ray.getPointAtDistance(tmin);
        }
        return null;
    }

    //TODO rewrite this
    /*
      two problems:
      1) will almost surely return true if one of the axis of the box extends to infinity
      2) will return true if box is large enough and is behind player
     */
    public boolean betweenRays(Vector pos, Vector dir1, Vector dir2) {
        if(dir1.dot(dir2) > 0.999) {
            //Directions are very similar; do a simple ray check.
            if(this.intersectsRay(new Ray(pos, dir2), 0, Float.MAX_VALUE) == null) {
                return false;
            }
        }
        else {
            //check if box even collides with plane
            Vector planeNormal = dir2.clone().crossProduct(dir1);
            Vector[] vertices = this.getVertices();
            boolean hitPlane = false;
            boolean above = false;
            boolean below = false;
            for(Vector vertex : vertices) {
                //Eh, what the hell. Let's move the vertices now.
                //Imagine moving everything in this system so that the plane
                //is at <0,0,0>. This will make it easier to compute stuff.
                vertex.subtract(pos);

                if(!hitPlane) {
                    if (vertex.dot(planeNormal) > 0) {
                        above = true;
                    } else {
                        below = true;
                    }
                    if (above && below) {
                        hitPlane = true;
                        //Yeah, don't break since we still need to move all the vertices.
                        //This code is under if(!hitPlane) so that we don't keep
                        //doing pointless math after we already determined that it
                        //hit the plane.
                    }
                }
            }
            if(!hitPlane) {
                return false;
            }

            //check if box is between both vectors
            Vector extraDirToDirNormal = planeNormal.clone().crossProduct(dir2);
            Vector dirToExtraDirNormal = dir1.clone().crossProduct(planeNormal);
            boolean betweenVectors = false;
            boolean frontOfExtraDirToDir = false;
            boolean frontOfDirToExtraDir = false;
            for(Vector vertex : vertices) {
                if(!frontOfExtraDirToDir && vertex.dot(extraDirToDirNormal) >= 0) {
                    frontOfExtraDirToDir = true;
                }
                if(!frontOfDirToExtraDir && vertex.dot(dirToExtraDirNormal) >= 0) {
                    frontOfDirToExtraDir = true;
                }

                if(frontOfExtraDirToDir && frontOfDirToExtraDir) {
                    betweenVectors = true;
                    break;
                }
            }
            if(!betweenVectors) {
                return false;
            }
        }

        return true;
    }

    public void highlight(CrystalAC crystal, World world, double accuracy) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(crystal, () -> {
            for (double x = min.getX(); x <= max.getX(); x += accuracy) {
                for (double y = min.getY(); y <= max.getY(); y += accuracy) {
                    for (double z = min.getZ(); z <= max.getZ(); z += accuracy) {
                        Vector position = new Vector(x, y, z);
                        world.playEffect(position.toLocation(world), Effect.COLOURED_DUST, 1);
                        world.playEffect(position.toLocation(world), Effect.COLOURED_DUST, 1);
                    }
                }
            }
        }, 0L);

    }

    public void translate(Vector vector) {
        min.add(vector);
        max.add(vector);
    }

    //translate AABB so that the min point is located at the given vector (AABB origin is min)
    public void translateTo(Vector vector) {
        max.setX(vector.getX() + (max.getX() - min.getX()));
        max.setY(vector.getY() + (max.getY() - min.getY()));
        max.setZ(vector.getZ() + (max.getZ() - min.getZ()));
        min.setX(vector.getX());
        min.setY(vector.getY());
        min.setZ(vector.getZ());
    }

    public boolean isColliding(AABB other) {
        if (max.getX() < other.getMin().getX() || min.getX() > other.getMax().getX()) {
            return false;
        }
        if (max.getY() < other.getMin().getY() || min.getY() > other.getMax().getY()) {
            return false;
        }
        return !(max.getZ() < other.getMin().getZ()) && !(min.getZ() > other.getMax().getZ());
    }

    public AABB clone() {
        AABB clone;
        try {
            clone = (AABB) super.clone();
            clone.min = this.min.clone();
            clone.max = this.max.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public double getVolume() {
        return (max.getX() - min.getX()) * (max.getY() - min.getY()) * (max.getZ() - min.getZ());
    }

    public Vector getMax() {
        return max;
    }

    public Vector getMin() {
        return min;
    }

    public Vector[] getVertices() {
        return new Vector[]{new Vector(min.getX(), min.getY(), min.getZ()),
                new Vector(min.getX(), min.getY(), max.getZ()),
                new Vector(min.getX(), max.getY(), min.getZ()),
                new Vector(min.getX(), max.getY(), max.getZ()),
                new Vector(max.getX(), min.getY(), min.getZ()),
                new Vector(max.getX(), min.getY(), max.getZ()),
                new Vector(max.getX(), max.getY(), min.getZ()),
                new Vector(max.getX(), max.getY(), max.getZ())};
    }

    public void shrink(double x, double y, double z) {
        Vector subtraction = new Vector(x, y, z);
        min.add(subtraction);
        max.subtract(subtraction);
    }

    public void expand(double x, double y, double z) {
        Vector compliment = new Vector(x, y, z);
        min.subtract(compliment);
        max.add(compliment);
    }

    public List<Block> getBlocks(World world) {
        List<Block> blocks = new ArrayList<>();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = ServerUtils.getBlockAsync(new Location(world, x, y, z));

                    if(block == null)
                        continue;

                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    public Set<Material> getMaterials(World world) {
        Set<Material> mats = new HashSet<>();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = ServerUtils.getBlockAsync(new Location(world, x, y, z));

                    if(block == null)
                        continue;

                    mats.add(block.getType());
                }
            }
        }
        return mats;
    }

    /**
     * Returns the shortest distance between this AABB and a point in space.
     * @param vector Point
     * @return Distance
     */
    public double distanceToPosition(Vector vector) {
        double distX = Math.max(min.getX() - vector.getX(), Math.max(0, vector.getX() - max.getX()));
        double distY = Math.max(min.getY() - vector.getY(), Math.max(0, vector.getY() - max.getY()));
        double distZ = Math.max(min.getZ() - vector.getZ(), Math.max(0, vector.getZ() - max.getZ()));
        return Math.sqrt(distX*distX + distY*distY + distZ*distZ);
    }

    @Override
    public String toString() {
        return "AABB{" +
                "min=" + min +
                ", max=" + max +
                '}';
    }
}
