/*
 * Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
 * For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
 */

package edu.gemini.observe.server.niri;

public enum Camera {
    F6("f/6"),
    F14("f/14"),
    F32("f/32");

    final String name;

    Camera(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}