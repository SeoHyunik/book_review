package com.example.macronews.service.market;

import com.example.macronews.dto.market.DxySnapshotDto;
import java.util.Optional;

public interface DxyProvider {

    Optional<DxySnapshotDto> getDxy();

    boolean isConfigured();
}
