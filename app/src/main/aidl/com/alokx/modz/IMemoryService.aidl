package com.alokx.modz;

interface IMemoryService {
    int attach(String pkg);
    int getPid();
    long getModuleBase(String name);
    byte[] readMem(long address, int size);
    float[] snapshot();
    void setCameraRotation(float x, float y, float z, float w);
    void setNoSpread(boolean on);
    String status();
}
