package com.hpophh.chuanggaosim.cg;

public class SportLatLngBean {
    public Double a;                    // 纬度
    public double ac;                   // 海拔
    public double d;                    // 这次到上次的距离米
    public double da;                   // 总距离，单位米
    public transient int id;            // 这个id是数据库的id，和服务器无关
    public Double o;                    // 经度
    public double s;                    // 速度
    public transient String sportBean_id;
    public int st;                      // 步频，似乎恒为 0 就可以
    public int sta;                     // 总步数，似乎恒为 0 就可以
    public long t;                      // 时间戳，毫秒
    public int v;                       // 未知参数，似乎可以恒为 1
}
