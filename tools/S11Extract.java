import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;

class S11Extract {
    static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        try (var recording = new RecordingFile(Path.of(args[0]))) {
            System.out.print("{\"recording\":{\"events\":[");
            boolean separator = false;
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                String type = event.getEventType().getName();
                String values = switch (type) {
                    case "minecraft.PacketSent" -> "\"remoteAddress\":" + quote(event.getString("remoteAddress"))
                            + ",\"packetId\":" + quote(event.getString("packetId"))
                            + ",\"protocolId\":" + quote(event.getString("protocolId"))
                            + ",\"packetDirection\":" + quote(event.getString("packetDirection"))
                            + ",\"bytes\":" + event.getInt("bytes");
                    case "minecraft.NetworkSummary" -> "\"remoteAddress\":" + quote(event.getString("remoteAddress"))
                            + ",\"sentBytes\":" + event.getLong("sentBytes")
                            + ",\"sentPackets\":" + event.getInt("sentPackets");
                    case "strategos.S11Tick" -> "\"scene\":" + quote(event.getString("scene"))
                            + ",\"coreTick\":" + event.getLong("coreTick")
                            + ",\"hits\":" + event.getLong("hits")
                            + ",\"placements\":" + event.getLong("placements")
                            + ",\"maxUnits\":" + event.getInt("maxUnits")
                            + ",\"movingSamples\":" + event.getInt("movingSamples")
                            + ",\"fogEdgeSamples\":" + event.getInt("fogEdgeSamples")
                            + ",\"panA\":" + event.getDouble("panA")
                            + ",\"panB\":" + event.getDouble("panB");
                    default -> null;
                };
                if (values == null) continue;
                if (separator) System.out.println(",");
                separator = true;
                System.out.print("{\"type\":" + quote(type) + ",\"values\":{\"startTime\":"
                        + quote(event.getStartTime().toString()) + "," + values + "}}");
            }
            System.out.println("]}}");
        }
    }
}
