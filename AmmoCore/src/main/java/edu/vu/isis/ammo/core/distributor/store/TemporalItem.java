/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */



package edu.vu.isis.ammo.core.distributor.store;

import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.provider.TemporalState;

/**
 * This class is primarily concerned with deriving the TemporalState.
 */
abstract public class TemporalItem {
    static private final Logger logger = LoggerFactory.getLogger("class.temporal.item");
    public static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");
    /**
     * The time delay from last seen to absent.
     */
    public static final long ABSENT_LIFESPAN = 20 * 60 * 1000;
    public static final long LOST_LIFESPAN = 5 * 60 * 1000;
    public static final long MISSED_LIFESPAN = 1 * 60 * 1000;
    public static final long RARE_RATE_LIMIT = 1;

    public final long first;
    public long latest;
    public int count;

    public TemporalItem() {
        this.first = System.currentTimeMillis();
        this.latest = this.first;
        this.count = 1;
    }

    public void update() {
        this.latest = Long.valueOf(System.currentTimeMillis());
        this.count++;
    }

    public long getExpiration() {
        return 0;
    }

    /**
     * The total state may consist of several minor states.
     * This method extracts a single dominant state.
     * 
     * @return dominant state.
     */
    public TemporalState getDominantState() {
        final long now = System.currentTimeMillis();
        final long elapsedTime = now - this.latest;
        logger.debug("get elapsed=[{}] state=[{}]", elapsedTime, this);

        if (elapsedTime > ABSENT_LIFESPAN)
            return TemporalState.ABSENT;
        if (elapsedTime > LOST_LIFESPAN)
            return TemporalState.LOST;
        if (elapsedTime > MISSED_LIFESPAN)
            return TemporalState.MISSED;
        if (this.latest != this.first && (count / (this.latest - this.first)) < RARE_RATE_LIMIT)
            return TemporalState.RARE;
        return TemporalState.PRESENT;
    }

    @Override
    public String toString() {
        return new StringBuilder().
                append("first=\"").append(SDF.format(this.first)).append("\",").
                append("latest=\"").append(SDF.format(this.latest)).append("\",").
                append("count=\"").append(this.count).append("\"").
                toString();
    }
}
