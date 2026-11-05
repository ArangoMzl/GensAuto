package dev.gensauto;

import dev.gensauto.route.Playlist;
import dev.gensauto.route.Route;
import dev.gensauto.route.RouteStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RouteStoreTest {
    private static final String SERVER = "blurkit.net", DIM = "minecraft:overworld";

    @Test void listsAndRoutesSurviveRestart(@TempDir Path dir) {
        Path file = dir.resolve("gensauto-routes.json");
        var store = new RouteStore(file);
        Playlist farm = store.active(SERVER, Playlist.FARM);
        Playlist mine = store.active(SERVER, Playlist.MINE);
        Playlist farm2 = store.create(SERVER, Playlist.FARM);
        store.rename(farm2, "Farm norte");
        store.setActive(farm2);
        store.add(route("PICO DE MINERIA", mine, 0));
        store.add(route("AZADA COSECHADORA", farm2, 0));

        var reopened = new RouteStore(file); // like closing and opening the game
        assertEquals("Farm norte", reopened.active(SERVER, Playlist.FARM).name);
        assertEquals(2, reopened.playlists(SERVER, Playlist.FARM).size());
        assertEquals(1, reopened.routes(reopened.active(SERVER, Playlist.MINE)).size());
        assertEquals(1, reopened.routes(reopened.active(SERVER, Playlist.FARM)).size());
        assertEquals(0, reopened.routes(reopened.playlists(SERVER, Playlist.FARM).get(0)).size());
        assertEquals(farm.id, reopened.playlists(SERVER, Playlist.FARM).get(0).id);
    }

    @Test void oldRoutesMoveToTheListOfTheirTool(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gensauto-routes.json");
        Files.writeString(file, "{\"routes\":[{\"id\":\"a\",\"name\":\"Azada 1\",\"server\":\"blurkit.net\",\"dimension\":\"minecraft:overworld\","
                + "\"tool\":\"AZADA COSECHADORA\",\"inPlaylist\":true,\"frames\":[0,0,0,0,0,0,0.5,0,0,0,0,0]},"
                + "{\"id\":\"b\",\"name\":\"Pico 1\",\"server\":\"blurkit.net\",\"dimension\":\"minecraft:overworld\","
                + "\"tool\":\"PICO DE MINERIA\",\"inPlaylist\":true,\"frames\":[0,0,0,0,0,0,0.5,0,0,0,0,0]}]}");
        var store = new RouteStore(file);
        assertEquals("a", store.routes(store.active(SERVER, Playlist.FARM)).get(0).id);
        assertEquals("b", store.routes(store.active(SERVER, Playlist.MINE)).get(0).id);
        assertEquals("Farm", store.active(SERVER, Playlist.FARM).name);
        assertEquals("Minería", store.active(SERVER, Playlist.MINE).name);
    }

    @Test void playsOnlyEnabledRoutesStartingNearYou(@TempDir Path dir) {
        var store = new RouteStore(dir.resolve("r.json"));
        Playlist farm = store.active(SERVER, Playlist.FARM);
        Route here = route("AZADA COSECHADORA", farm, 0), far = route("AZADA COSECHADORA", farm, 50), off = route("AZADA COSECHADORA", farm, 0);
        off.inPlaylist = false;
        store.add(here); store.add(far); store.add(off);
        var list = store.playable(farm, DIM, 1, 0, 0, 3);
        assertEquals(1, list.size());
        assertSame(here, list.get(0));
        assertSame(far, store.nearestStart(farm, DIM, 48, 0, 0));
    }

    @Test void deletingAListDeletesItsRoutesAndKeepsOneList(@TempDir Path dir) {
        var store = new RouteStore(dir.resolve("r.json"));
        Playlist farm = store.active(SERVER, Playlist.FARM);
        store.add(route("AZADA COSECHADORA", farm, 0));
        store.delete(farm);
        Playlist again = store.active(SERVER, Playlist.FARM);
        assertNotEquals(farm.id, again.id);
        assertEquals(0, store.routes(again).size());
        assertEquals(1, store.playlists(SERVER, Playlist.FARM).size());
    }

    private static Route route(String tool, Playlist p, double startX) {
        Route r = new Route();
        r.tool = tool; r.server = SERVER; r.dimension = DIM; r.playlist = p.id; r.name = tool + startX;
        r.startX = startX;
        r.frames = new float[]{0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0.2f, 0, 0, 0, 0, 0};
        return r;
    }
}
