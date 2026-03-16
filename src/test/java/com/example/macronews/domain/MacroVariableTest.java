package com.example.macronews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MacroVariableTest {

    @Test
    @DisplayName("sentimentFor should follow explicit domain policy per macro variable")
    void sentimentFor_appliesVariableSpecificPolicy() {
        assertThat(MacroVariable.KOSPI.sentimentFor(ImpactDirection.UP)).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(MacroVariable.KOSDAQ.sentimentFor(ImpactDirection.DOWN)).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(MacroVariable.VOLATILITY.sentimentFor(ImpactDirection.DOWN)).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(MacroVariable.OIL.sentimentFor(ImpactDirection.UP)).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(MacroVariable.USD.sentimentFor(ImpactDirection.DOWN)).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(MacroVariable.INFLATION.sentimentFor(ImpactDirection.UP)).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(MacroVariable.INTEREST_RATE.sentimentFor(ImpactDirection.UP)).isEqualTo(SignalSentiment.NEUTRAL);
        assertThat(MacroVariable.GOLD.sentimentFor(ImpactDirection.DOWN)).isEqualTo(SignalSentiment.NEUTRAL);
    }
}
