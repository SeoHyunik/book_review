package com.example.macronews.domain;

public enum MacroVariable {
    KOSPI(Directionality.PRO_CYCLICAL),
    KOSDAQ(Directionality.PRO_CYCLICAL),
    VOLATILITY(Directionality.INVERSE_RISK),
    OIL(Directionality.INVERSE_COST),
    USD(Directionality.INVERSE_LIQUIDITY),
    INTEREST_RATE(Directionality.AMBIGUOUS_POLICY),
    INFLATION(Directionality.INVERSE_COST),
    GOLD(Directionality.AMBIGUOUS_SAFE_HAVEN);

    private final Directionality directionality;

    MacroVariable(Directionality directionality) {
        this.directionality = directionality;
    }

    public SignalSentiment sentimentFor(ImpactDirection direction) {
        if (direction == null || direction == ImpactDirection.NEUTRAL) {
            return SignalSentiment.NEUTRAL;
        }
        return directionality.sentimentFor(direction);
    }

    public boolean isAmbiguous() {
        return directionality == Directionality.AMBIGUOUS_POLICY
                || directionality == Directionality.AMBIGUOUS_SAFE_HAVEN;
    }

    private enum Directionality {
        PRO_CYCLICAL {
            @Override
            SignalSentiment sentimentFor(ImpactDirection direction) {
                return direction == ImpactDirection.UP ? SignalSentiment.POSITIVE : SignalSentiment.NEGATIVE;
            }
        },
        INVERSE_RISK {
            @Override
            SignalSentiment sentimentFor(ImpactDirection direction) {
                return direction == ImpactDirection.DOWN ? SignalSentiment.POSITIVE : SignalSentiment.NEGATIVE;
            }
        },
        INVERSE_COST {
            @Override
            SignalSentiment sentimentFor(ImpactDirection direction) {
                return direction == ImpactDirection.DOWN ? SignalSentiment.POSITIVE : SignalSentiment.NEGATIVE;
            }
        },
        INVERSE_LIQUIDITY {
            @Override
            SignalSentiment sentimentFor(ImpactDirection direction) {
                return direction == ImpactDirection.DOWN ? SignalSentiment.POSITIVE : SignalSentiment.NEGATIVE;
            }
        },
        AMBIGUOUS_POLICY {
            @Override
            SignalSentiment sentimentFor(ImpactDirection direction) {
                return SignalSentiment.NEUTRAL;
            }
        },
        AMBIGUOUS_SAFE_HAVEN {
            @Override
            SignalSentiment sentimentFor(ImpactDirection direction) {
                return SignalSentiment.NEUTRAL;
            }
        };

        abstract SignalSentiment sentimentFor(ImpactDirection direction);
    }
}
