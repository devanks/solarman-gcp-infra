package dev.devanks.solarman.archiver.service.io; // example package

import org.springframework.core.io.WritableResource;

public interface GcsWritableResource extends WritableResource {
    // Potentially keep getOutputStream here if WritableResource has it,
    // or add specific methods if WritableResource is too broad.
    // WritableResource already has getOutputStream()
}
