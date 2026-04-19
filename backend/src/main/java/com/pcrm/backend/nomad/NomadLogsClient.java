package com.pcrm.backend.nomad;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface NomadLogsClient {

    List<NomadAllocationSnapshot> listJobAllocations(String nomadJobId);

    void streamAllocationLogs(
            String allocationId,
            String streamType,
            boolean follow,
            long offset,
            Consumer<NomadLogFrame> frameConsumer
    ) throws IOException, InterruptedException;

    record NomadAllocationSnapshot(
            String id,
            String clientStatus,
            long createIndex,
            long modifyIndex
    ) {
    }

    record NomadLogFrame(
            NomadLogFrameType type,
            String chunk,
            long offset,
            int byteLength,
            String message
    ) {
        public static NomadLogFrame chunk(String chunk, long offset, int byteLength) {
            return new NomadLogFrame(NomadLogFrameType.CHUNK, chunk, offset, byteLength, null);
        }

        public static NomadLogFrame heartbeat() {
            return new NomadLogFrame(NomadLogFrameType.HEARTBEAT, null, -1L, 0, null);
        }

        public static NomadLogFrame status(String message, long offset) {
            return new NomadLogFrame(NomadLogFrameType.STATUS, null, offset, 0, message);
        }
    }

    enum NomadLogFrameType {
        CHUNK,
        STATUS,
        HEARTBEAT
    }
}
