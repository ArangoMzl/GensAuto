package dev.gensauto;

import dev.gensauto.route.Route;
import dev.gensauto.route.RouteMath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RouteMathTest {
    @Test void movesWithTheRightKeys() {
        // Yaw 0 looks to +Z: forward is +Z, back is -Z, A (left) is +X, D (right) is -X.
        assertEquals(Route.FWD, RouteMath.moveKeys(0, 1, 0));
        assertEquals(Route.BACK, RouteMath.moveKeys(0, -1, 0));
        assertEquals(Route.LEFT, RouteMath.moveKeys(1, 0, 0));
        assertEquals(Route.RIGHT, RouteMath.moveKeys(-1, 0, 0));
        assertEquals(Route.FWD | Route.LEFT, RouteMath.moveKeys(1, 1, 0));
        // Yaw 90 looks to -X.
        assertEquals(Route.FWD, RouteMath.moveKeys(-1, 0, 90));
        assertEquals(0, RouteMath.moveKeys(0, 0, 0));
    }

    @Test void tellsWhereTheStartIs() {
        // Looking to +Z (yaw 0): +Z is ahead, -X is to the right (D), +X to the left (A), -Z behind.
        assertEquals("adelante", RouteMath.direction(0, 5, 0));
        assertEquals("a la derecha", RouteMath.direction(-5, 0, 0));
        assertEquals("a la izquierda", RouteMath.direction(5, 0, 0));
        assertEquals("atrás", RouteMath.direction(0, -5, 0));
        assertEquals("adelante a la derecha", RouteMath.direction(-5, 5, 0));
        // Looking to -X (yaw 90): -Z is to the right, +X behind.
        assertEquals("atrás a la derecha", RouteMath.direction(2, -4, 90));
    }

    @Test void wrapsDegrees() {
        assertEquals(-90, RouteMath.wrapDegrees(270), 1e-9);
        assertEquals(170, RouteMath.wrapDegrees(-190), 1e-9);
    }

    @Test void neverRepeatsTheSameRouteWhenThereIsAnother() {
        Route a = route("a"), b = route("b");
        var random = new Random(1);
        for (int i = 0; i < 50; i++) assertEquals("b", RouteMath.pickNext(List.of(a, b), "a", random).id);
        assertEquals("a", RouteMath.pickNext(List.of(a), "a", random).id);
        assertNull(RouteMath.pickNext(List.of(), null, random));
    }

    @Test void knowsWhenARouteEndsAtTheStart() {
        Route r = route("r");
        r.frames = new float[]{0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 1, 0, 0.5f, 0, 0, 0};
        assertEquals(3, r.size());
        assertTrue(r.closed());
        r.frames = new float[]{0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0};
        assertFalse(r.closed());
    }

    private static Route route(String id) {
        Route r = new Route();
        r.id = id;
        return r;
    }
}
