package com.example.macronews.service.market;

import com.example.macronews.dto.market.Us10ySnapshotDto;
import java.util.Optional;

public interface Us10yProvider {

    Optional<Us10ySnapshotDto> getUs10y();

    boolean isConfigured();
}
