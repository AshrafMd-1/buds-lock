package dev.ashraf.buds4readonly.protocol;

public record GestureRecord(int index, int deviceType, int deviceButton, int buttonAction, int function) {
    public String hexLine() {
        return String.format("[%02d] DT=%02X DB=%02X BA=%02X FN=%02X",
                index, deviceType, deviceButton, buttonAction, function);
    }
}
