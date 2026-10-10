package rocketleague;

import rlbot.flat.RotatorT;
import rlbot.flat.Vector3T;

// A simple 3D vector for the aerial math
public record Vec3(float x, float y, float z) {

    public static Vec3 of(Vector3T v) {
        return new Vec3(v.getX(), v.getY(), v.getZ());
    }

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(float s) { return new Vec3(x * s, y * s, z * s); }
    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public float length() { return (float) Math.sqrt(dot(this)); }
    public Vec3 flat() { return new Vec3(x, y, 0); }

    public Vec3 normalized() {
        float len = length();
        return len == 0 ? this : scale(1 / len);
    }

    // The direction the car's nose points
    public static Vec3 forward(RotatorT r) {
        float cp = (float) Math.cos(r.getPitch()), sp = (float) Math.sin(r.getPitch());
        float cy = (float) Math.cos(r.getYaw()), sy = (float) Math.sin(r.getYaw());
        return new Vec3(cp * cy, cp * sy, sp);
    }

    // The direction out of the car's right side
    public static Vec3 right(RotatorT r) {
        float cp = (float) Math.cos(r.getPitch()), sp = (float) Math.sin(r.getPitch());
        float cy = (float) Math.cos(r.getYaw()), sy = (float) Math.sin(r.getYaw());
        float cr = (float) Math.cos(r.getRoll()), sr = (float) Math.sin(r.getRoll());
        return new Vec3(cy * sp * sr - cr * sy, sy * sp * sr + cr * cy, -cp * sr);
    }

    // The direction out of the car's roof
    public static Vec3 up(RotatorT r) {
        float cp = (float) Math.cos(r.getPitch()), sp = (float) Math.sin(r.getPitch());
        float cy = (float) Math.cos(r.getYaw()), sy = (float) Math.sin(r.getYaw());
        float cr = (float) Math.cos(r.getRoll()), sr = (float) Math.sin(r.getRoll());
        return new Vec3(-cr * cy * sp - sr * sy, -cr * sy * sp + sr * cy, cp * cr);
    }
}
