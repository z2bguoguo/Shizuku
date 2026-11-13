package rikka.shizuku.server.concurrent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/** Synchronous, bounded round-robin admission. A timed-out waiter never executes later. */
public final class FairCallQueue {
    private static final class Ticket { final Object owner; Ticket(Object owner) { this.owner = owner; } }
    private static final class Lane {
        final Map<Object, ArrayDeque<Ticket>> owners = new LinkedHashMap<>();
        final ArrayDeque<Object> rotation = new ArrayDeque<>();
        boolean active;
        int waiting;
        long completed, rejected;
        Ticket next() { return rotation.isEmpty() ? null : owners.get(rotation.peekFirst()).peekFirst(); }
        void remove(Ticket ticket, boolean admitted) {
            ArrayDeque<Ticket> list = owners.get(ticket.owner);
            if (list == null || !list.remove(ticket)) return;
            waiting--;
            if (list.isEmpty()) { owners.remove(ticket.owner); rotation.remove(ticket.owner); }
            else if (admitted) { rotation.remove(ticket.owner); rotation.addLast(ticket.owner); }
        }
    }
    private final Map<String, Lane> lanes = new HashMap<>();
    private synchronized Lane lane(String key) { return lanes.computeIfAbsent(key, ignored -> new Lane()); }

    public <T> T call(String key, Object owner, long timeoutMs, Callable<T> operation) throws Exception {
        Lane lane = lane(key);
        Ticket ticket = new Ticket(owner);
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        synchronized (lane) {
            ArrayDeque<Ticket> own = lane.owners.get(owner);
            if (lane.waiting >= 8 || (own != null && own.size() >= 4)) {
                lane.rejected++;
                throw new IllegalStateException("Resource queue is full: " + key);
            }
            if (own == null) { own = new ArrayDeque<>(); lane.owners.put(owner, own); lane.rotation.addLast(owner); }
            own.addLast(ticket); lane.waiting++;
            try {
                while (lane.active || lane.next() != ticket) {
                    if (!lane.owners.containsKey(owner)) throw new IllegalStateException("Caller disconnected");
                    long left = deadline - System.nanoTime();
                    if (left <= 0) { lane.rejected++; throw new TimeoutException("Timed out waiting for resource: " + key); }
                    lane.wait(Math.max(1, Math.min(100, left / 1_000_000L)));
                }
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                lane.remove(ticket, true);
                lane.active = true;
            } catch (Exception error) { lane.remove(ticket, false); lane.notifyAll(); throw error; }
        }
        try { return operation.call(); }
        finally { synchronized (lane) { lane.active = false; lane.completed++; lane.notifyAll(); } }
    }
    public void cancel(Object owner) {
        Lane[] copy;
        synchronized (this) { copy = lanes.values().toArray(new Lane[0]); }
        for (Lane lane : copy) synchronized (lane) {
            ArrayDeque<Ticket> removed = lane.owners.remove(owner);
            if (removed != null) { lane.waiting -= removed.size(); lane.rotation.remove(owner); lane.notifyAll(); }
        }
    }
    public synchronized String diagnostics() {
        StringBuilder result = new StringBuilder("Resource queues (round-robin; max 8 waiters/lane):\n");
        for (Map.Entry<String, Lane> entry : lanes.entrySet()) synchronized (entry.getValue()) {
            Lane lane = entry.getValue();
            result.append(entry.getKey()).append(" active=").append(lane.active).append(" waiting=")
                    .append(lane.waiting).append(" completed=").append(lane.completed).append(" rejected=").append(lane.rejected).append('\n');
        }
        return result.toString();
    }
}
