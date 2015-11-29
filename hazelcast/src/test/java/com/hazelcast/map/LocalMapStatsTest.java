package com.hazelcast.map;

import com.hazelcast.config.Config;
import com.hazelcast.config.MemberGroupConfig;
import com.hazelcast.config.PartitionGroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.nio.Address;
import com.hazelcast.partition.InternalPartitionService;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.test.annotation.Repeat;
import com.hazelcast.util.Clock;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class LocalMapStatsTest extends HazelcastTestSupport {

    @Test
    public void testHitsGenerated() throws Exception {
        IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
            map.get(i);
        }
        LocalMapStats localMapStats = map.getLocalMapStats();
        assertEquals(100, localMapStats.getHits());
    }

    @Test
    public void testPutAndHitsGenerated() throws Exception {
        IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
            map.get(i);
        }
        LocalMapStats localMapStats = map.getLocalMapStats();
        assertEquals(100, localMapStats.getPutOperationCount());
        assertEquals(100, localMapStats.getHits());
    }

    @Test
    public void testPutAsync() throws Exception {
        IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.putAsync(i, i);
        }
        final LocalMapStats localMapStats = map.getLocalMapStats();
        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                assertEquals(100, localMapStats.getPutOperationCount());
            }
        });
    }

    @Test
    public void testGetAndHitsGenerated() throws Exception {
        IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
            map.get(i);
        }
        LocalMapStats localMapStats = map.getLocalMapStats();
        assertEquals(100, localMapStats.getGetOperationCount());
        assertEquals(100, localMapStats.getHits());
    }

    @Test
    public void testGetAsyncAndHitsGenerated() throws Exception {
        final IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
            map.getAsync(i).get();
        }

        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                final LocalMapStats localMapStats = map.getLocalMapStats();
                assertEquals(100, localMapStats.getGetOperationCount());
                assertEquals(100, localMapStats.getHits());
            }
        });
    }

    @Test
    public void testRemove() throws Exception {
        IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
            map.remove(i);
        }
        LocalMapStats localMapStats = map.getLocalMapStats();
        assertEquals(100, localMapStats.getRemoveOperationCount());
    }

    @Test
    public void testRemoveAsync() throws Exception {
        IMap<Integer, Integer> map = getMap();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
            map.removeAsync(i);
        }
        final LocalMapStats localMapStats = map.getLocalMapStats();
        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                assertEquals(100, localMapStats.getRemoveOperationCount());
            }
        });
    }

    @Test
    public void testHitsGenerated_updatedConcurrently() throws Exception {
        final IMap<Integer, Integer> map = getMap();
        final int actionCount = 100;
        for (int i = 0; i < actionCount; i++) {
            map.put(i, i);
            map.get(i);
        }
        final LocalMapStats localMapStats = map.getLocalMapStats();
        final long initialHits = localMapStats.getHits();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < actionCount; i++) {
                    map.get(i);
                }
                map.getLocalMapStats(); // causes the local stats object to update
            }
        }).start();

        assertEquals(actionCount, initialHits);
        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                assertEquals(actionCount * 2, localMapStats.getHits());
            }
        });
    }

    @Test
    public void testLastAccessTime() throws InterruptedException {
        final long startTime = Clock.currentTimeMillis();

        IMap<String, String> map = getMap();

        String key = "key";
        map.put(key, "value");

        long lastUpdateTime = map.getLocalMapStats().getLastUpdateTime();
        assertTrue(lastUpdateTime >= startTime);

        Thread.sleep(5);
        map.put(key, "value2");
        long lastUpdateTime2 = map.getLocalMapStats().getLastUpdateTime();
        assertTrue(lastUpdateTime2 > lastUpdateTime);
    }

    @Test
    public void testLastAccessTime_updatedConcurrently() throws InterruptedException {
        final long startTime = Clock.currentTimeMillis();
        final IMap<String, String> map = getMap();

        final String key = "key";
        map.put(key, "value");

        final LocalMapStats localMapStats = map.getLocalMapStats();
        final long lastUpdateTime = localMapStats.getLastUpdateTime();

        new Thread(new Runnable() {
            @Override
            public void run() {
                sleepAtLeastMillis(1);
                map.put(key, "value2");
                map.getLocalMapStats(); // causes the local stats object to update
            }
        }).start();

        assertTrue(lastUpdateTime >= startTime);
        assertTrueEventually(new AssertTask() {
            @Override
            public void run()
                    throws Exception {
                assertTrue(localMapStats.getLastUpdateTime() > lastUpdateTime);
            }
        });
    }

    @Test
    public void testEvictAll() throws Exception {
        IMap<String, String> map = getMap();
        map.put("key", "value");
        map.evictAll();

        final long heapCost = map.getLocalMapStats().getHeapCost();

        assertEquals(0L, heapCost);
    }

    @Test
    public void testHits_whenMultipleNodes() throws InterruptedException {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(2);
        final HazelcastInstance[] instances = factory.newInstances(getConfig());
        MultiMap<Object, Object> multiMap0 = instances[0].getMultiMap("testHits_whenMultipleNodes");
        MultiMap<Object, Object> multiMap1 = instances[1].getMultiMap("testHits_whenMultipleNodes");

        // InternalPartitionService is used in order to determine owners of these keys that we will use.
        InternalPartitionService partitionService = getNode(instances[0]).getPartitionService();
        Address address = partitionService.getPartitionOwner(partitionService.getPartitionId("test1"));

        boolean inFirstInstance = address.equals(getNode(instances[0]).getThisAddress());
        multiMap0.get("test0");

        multiMap0.put("test1", 1);
        multiMap1.get("test1");

        assertEquals(inFirstInstance ? 1 : 0, multiMap0.getLocalMultiMapStats().getHits());
        assertEquals(inFirstInstance ? 0 : 1, multiMap1.getLocalMultiMapStats().getHits());

        multiMap0.get("test1");
        multiMap1.get("test1");
        assertEquals(inFirstInstance ? 0 : 3, multiMap1.getLocalMultiMapStats().getHits());
    }

    @Test
    public void testPutStats_afterPutAll() {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(2);
        final HazelcastInstance[] instances = factory.newInstances(getConfig());
        Map map = new HashMap();
        for (int i = 1; i <= 5000; i++) map.put(i, i);

        IMap iMap = instances[0].getMap("example");
        iMap.putAll(map);
        final LocalMapStats localMapStats = iMap.getLocalMapStats();
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(5000, localMapStats.getPutOperationCount());
            }
        });

    }

    @Test
    public void testLocalMapStats_withMemberGroups() throws Exception {
        final String mapName = randomMapName();
        final String[] firstMemberGroup = {"127.0.0.1", "127.0.0.2"};
        final String[] secondMemberGroup = {"127.0.0.3"};

        final Config config = createConfig(mapName, firstMemberGroup, secondMemberGroup);
        final String[] addressArray = concatenateArrays(firstMemberGroup, secondMemberGroup);

        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(addressArray);
        final HazelcastInstance node1 = factory.newHazelcastInstance(config);
        final HazelcastInstance node2 = factory.newHazelcastInstance(config);
        final HazelcastInstance node3 = factory.newHazelcastInstance(config);

        final IMap<Object, Object> test = node3.getMap(mapName);
        test.put(1, 1);

        assertBackupEntryCount(1, mapName, factory.getAllHazelcastInstances());
    }

    private void assertBackupEntryCount(final long expectedBackupEntryCount, final String mapName,
                                        final Collection<HazelcastInstance> nodes) {

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                long backup = 0;
                for (HazelcastInstance node : nodes) {
                    final IMap<Object, Object> map = node.getMap(mapName);
                    backup += getBackupEntryCount(map);
                }
                assertEquals(expectedBackupEntryCount, backup);
            }
        });
    }

    private long getBackupEntryCount(IMap<Object, Object> map) {
        final LocalMapStats localMapStats = map.getLocalMapStats();
        return localMapStats.getBackupEntryCount();
    }

    private String[] concatenateArrays(String[]... arrays) {
        int len = 0;
        for (String[] array : arrays) {
            len += array.length;
        }
        String[] result = new String[len];
        int destPos = 0;
        for (String[] array : arrays) {
            System.arraycopy(array, 0, result, destPos, array.length);
            destPos += array.length;
        }
        return result;
    }

    private Config createConfig(String mapName, String[] firstGroup, String[] secondGroup) {
        final MemberGroupConfig firstGroupConfig = createGroupConfig(firstGroup);
        final MemberGroupConfig secondGroupConfig = createGroupConfig(secondGroup);

        Config config = getConfig();
        config.getPartitionGroupConfig().setEnabled(true)
                .setGroupType(PartitionGroupConfig.MemberGroupType.CUSTOM);
        config.getPartitionGroupConfig().addMemberGroupConfig(firstGroupConfig);
        config.getPartitionGroupConfig().addMemberGroupConfig(secondGroupConfig);
        config.getNetworkConfig().getInterfaces().addInterface("127.0.0.*");

        config.getMapConfig(mapName).setBackupCount(2);

        return config;
    }

    private MemberGroupConfig createGroupConfig(String[] addressArray) {
        final MemberGroupConfig memberGroupConfig = new MemberGroupConfig();
        for (String address : addressArray) {
            memberGroupConfig.addInterface(address);
        }
        return memberGroupConfig;
    }

    private <K, V> IMap<K, V> getMap() {
        HazelcastInstance instance = createHazelcastInstance(getConfig());
        return instance.getMap(randomString());
    }
}
