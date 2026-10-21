package dev.strategos.core.sim.archfixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA-10's violating fixture: a sim class logging around MatchLog. */
public final class LoggerFactoryFixture {
    static final Logger LOG = LoggerFactory.getLogger(LoggerFactoryFixture.class);

    private LoggerFactoryFixture() {}
}
