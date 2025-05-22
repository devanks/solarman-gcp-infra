// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/client/SolarmanApiClient.java
package dev.devanks.solarman.ingestor.client;

import dev.devanks.solarman.ingestor.config.SolarmanApiClientConfig;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Feign client for interacting with the Solarman API.
 * Authentication is handled by SolarmanApiClientConfig.
 */
@FeignClient(name = "solarman-api", // Logical name
        url = "${ingestor.gcp.solarman-api-url}", // Get base URL from properties
        configuration = SolarmanApiClientConfig.class)
public interface SolarmanApiClient {

    // TODO: Verify the exact path needed - adjust "/maintain-s/fast/system/3340235" as required.
    //  Parameterize this later for system ID
    @GetMapping("/maintain-s/fast/system/3340235")
    SolarmanAPIResponse getSystemData(); // Feign can deserialize directly

}
