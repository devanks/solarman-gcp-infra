// In GcsResourceProvider.java
package dev.devanks.solarman.archiver.service.io;

import com.google.cloud.spring.storage.GoogleStorageResource;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GcsResourceProvider {
    private final Storage gcsClient; // Injected here

    public GcsWritableResource createWritableResource(String gcsPath) {
        // It's important that GcsWritableResourceWrapper correctly implements GcsWritableResource
        // and delegates to the actual GoogleStorageResource.
        return new GcsWritableResourceWrapper(new GoogleStorageResource(gcsClient, gcsPath));
    }
}
