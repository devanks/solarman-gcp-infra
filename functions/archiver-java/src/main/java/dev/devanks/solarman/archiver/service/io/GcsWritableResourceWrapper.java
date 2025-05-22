package dev.devanks.solarman.archiver.service.io;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource; // For other methods if needed
import org.springframework.core.io.WritableResource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;


@RequiredArgsConstructor // from lombok
public class GcsWritableResourceWrapper implements GcsWritableResource {
    private final WritableResource delegate; // Actually GoogleStorageResource

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    // Delegate other WritableResource methods if used by the service
    @Override public boolean isWritable() { return delegate.isWritable(); }
    @Override public boolean exists() { return delegate.exists(); }
    @Override public URL getURL() throws IOException { return delegate.getURL(); }
    @Override public URI getURI() throws IOException { return delegate.getURI(); }
    @Override public File getFile() throws IOException { return delegate.getFile(); }
    @Override public ReadableByteChannel readableChannel() throws IOException { return delegate.readableChannel(); }
    @Override public long contentLength() throws IOException { return delegate.contentLength(); }
    @Override public long lastModified() throws IOException { return delegate.lastModified(); }
    @Override public Resource createRelative(String relativePath) throws IOException { return delegate.createRelative(relativePath); }
    @Override public String getFilename() { return delegate.getFilename(); }
    @Override public String getDescription() { return delegate.getDescription(); }
    @Override public InputStream getInputStream() throws IOException { return delegate.getInputStream(); }
    @Override public boolean isReadable() { return delegate.isReadable(); } // From Resource
    @Override public boolean isOpen() { return delegate.isOpen(); } // From Resource
    @Override public WritableByteChannel writableChannel() throws IOException { return delegate.writableChannel(); } // From WritableResource
}
