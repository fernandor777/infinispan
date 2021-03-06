package org.infinispan.query.backend;

import static org.infinispan.test.TestingUtil.withCacheManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.concurrent.atomic.LongAdder;

import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.spi.IndexedTypeIdentifier;
import org.infinispan.Cache;
import org.infinispan.commons.util.Util;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.eviction.EvictionType;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.event.CacheEntryActivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryPassivatedEvent;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.queries.faceting.Car;
import org.infinispan.query.test.Person;
import org.infinispan.query.test.QueryTestSCI;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.CacheManagerCallable;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test for interaction of activation and preload on indexing
 *
 * @author gustavonalle
 * @since 7.0
 */
@Test(groups = "functional", testName = "query.backend.QueryInterceptorTest")
public class QueryInterceptorTest extends AbstractInfinispanTest {

   private static final int MAX_CACHE_ENTRIES = 1;
   private File indexDir;
   private File storeDir;

   private final Person person1 = new Person("p1", "b1", 12);
   private final Person person2 = new Person("p2", "b2", 22);
   private final Car car1 = new Car("subaru", "blue", 200);
   private final Car car2 = new Car("lamborghini", "yellow", 230);

   @BeforeMethod
   protected void setup() throws Exception {
      indexDir = Files.createTempDirectory("test-").toFile();
      storeDir = Files.createTempDirectory("test-").toFile();
   }

   @AfterMethod
   protected void tearDown() {
      Util.recursiveFileRemove(indexDir);
      Util.recursiveFileRemove(storeDir);
   }

   @Test
   public void shouldNotReindexOnActivation() throws Exception {
      withCacheManager(new CacheManagerCallable(createCacheManager(MAX_CACHE_ENTRIES)) {
         @Override
         public void call() {
            LuceneIndexTracker luceneIndexTracker = new LuceneIndexTracker(new File(indexDir + "/person"));
            luceneIndexTracker.mark();

            Cache<Integer, Person> cache = cm.getCache();
            CacheListener cacheListener = new CacheListener();
            cache.addListener(cacheListener);

            cache.put(1, person1);
            cache.put(2, person2);

            // Notification is non blocking
            eventuallyEquals(1, cacheListener::numberOfPassivations);
            assertEquals(cacheListener.numberOfActivations(), 0);
            assertTrue(luceneIndexTracker.indexChanged());

            luceneIndexTracker.mark();

            cache.get(1);

            assertEquals(cacheListener.numberOfActivations(), 1);
            assertFalse(luceneIndexTracker.indexChanged());
         }
      });
   }

   @Test
   public void shouldNotReindexOnPreload() throws Exception {
      final LuceneIndexTracker luceneIndexTracker = new LuceneIndexTracker(new File(indexDir + "/person"));
      luceneIndexTracker.mark();

      withCacheManager(new CacheManagerCallable(createCacheManager(MAX_CACHE_ENTRIES)) {
         @Override
         public void call() {
            Cache<Integer, Person> cache = cm.getCache();
            cache.put(1, person1);
            cache.put(2, person2);
            assertTrue(luceneIndexTracker.indexChanged());
         }
      });

      luceneIndexTracker.mark();

      withCacheManager(new CacheManagerCallable(createCacheManager(MAX_CACHE_ENTRIES + 10)) {
         @Override
         public void call() {
            Cache<Integer, Person> cache = cm.getCache();
            CacheListener cacheListener = new CacheListener();
            cache.addListener(cacheListener);

            assertTrue(cache.containsKey(1));
            assertTrue(cache.containsKey(2));
            assertEquals(cacheListener.numberOfPassivations(), 0);
            assertEquals(cacheListener.numberOfActivations(), 0);
            assertFalse(luceneIndexTracker.indexChanged());
         }
      });

   }

   @Test
   public void shouldDeleteSingleIndex() throws Exception {
      withCacheManager(new CacheManagerCallable(createCacheManager(MAX_CACHE_ENTRIES)) {
         @Override
         public void call() {
            Cache<String, Object> cache = cm.getCache();
            cache.put("P1", person1);
            cache.put("P2", person2);
            cache.put("C1", car1);
            cache.put("C2", car2);
            SearchManager searchManager = Search.getSearchManager(cache);

            assertEquals(2, countIndex(Car.class, cache));
            assertEquals(2, countIndex(Person.class, cache));

            searchManager.purge(Car.class);
            assertEquals(0, countIndex(Car.class, cache));
            assertEquals(2, countIndex(Person.class, cache));

            searchManager.purge(Person.class);
            assertEquals(0, countIndex(Car.class, cache));
            assertEquals(0, countIndex(Person.class, cache));
         }
      });


   }

   @Test
   @SuppressWarnings("unchecked")
   public void shouldDeleteFromAllIndexesById() throws Exception {
      withCacheManager(new CacheManagerCallable(createVolatileCacheManager()) {
         @Override
         public void call() {
            Cache<String, Object> cache = cm.getCache();

            // Configure Query interceptor to ignore deletes of previous values
            SearchWorkCreator searchWorkCreator = new IgnoreDeletesSearchWorkCreator();
            QueryInterceptor queryInterceptor = cache.getAdvancedCache().getComponentRegistry().getComponent(QueryInterceptor.class);
            queryInterceptor.setSearchWorkCreator(searchWorkCreator);

            // Override entity
            cache.put("key", person1);
            cache.put("key", car1);

            // Old entity will be left in the indexes
            assertEquals(1, cache.size());
            assertEquals(1, countIndex(Person.class, cache));
            assertEquals(1, countIndex(Car.class, cache));

            // Remove by id from all indexes
            queryInterceptor.removeFromIndexes(NoTransactionContext.INSTANCE, "key");

            // Assert indexes are empty
            assertEquals(1, cache.size());
            assertEquals(0, countIndex(Person.class, cache));
            assertEquals(0, countIndex(Car.class, cache));
         }
      });
   }

   private static final class IgnoreDeletesSearchWorkCreator implements SearchWorkCreator {

      @Override
      public Work createPerEntityTypeWork(IndexedTypeIdentifier entityType, WorkType workType) {
         return SearchWorkCreator.DEFAULT.createPerEntityTypeWork(entityType, workType);
      }

      @Override
      public Work createPerEntityWork(Object entity, Serializable id, WorkType workType) {
         if (workType.equals(WorkType.DELETE)) {
            return null;
         }
         return SearchWorkCreator.DEFAULT.createPerEntityWork(entity, id, workType);
      }

      @Override
      public Work createPerEntityWork(Serializable id, IndexedTypeIdentifier entityType, WorkType workType) {
         return SearchWorkCreator.DEFAULT.createPerEntityWork(id, entityType, workType);
      }
   }

   protected EmbeddedCacheManager createCacheManager(int maxEntries) throws Exception {
      ConfigurationBuilder b = new ConfigurationBuilder();
      b.memory().evictionType(EvictionType.COUNT).size(maxEntries)
            .persistence().passivation(true)
            .addSingleFileStore().location(storeDir.getAbsolutePath()).preload(true)
            .indexing().index(Index.ALL)
            .addIndexedEntity(Person.class)
            .addIndexedEntity(Car.class)
            .addProperty("default.directory_provider", "filesystem")
            .addProperty("default.indexBase", indexDir.getAbsolutePath())
            .addProperty("lucene_version", "LUCENE_CURRENT");
      return TestCacheManagerFactory.createCacheManager(QueryTestSCI.INSTANCE, b);
   }

   protected EmbeddedCacheManager createVolatileCacheManager() {
      ConfigurationBuilder b = new ConfigurationBuilder();
      b.indexing().index(Index.ALL)
            .addIndexedEntity(Person.class)
            .addIndexedEntity(Car.class)
            .addProperty("default.directory_provider", "local-heap")
            .addProperty("lucene_version", "LUCENE_CURRENT");
      return TestCacheManagerFactory.createCacheManager(QueryTestSCI.INSTANCE, b);
   }

   private int countIndex(Class<?> entityType, Cache<?, ?> cache) {
      return Search.getSearchManager(cache).getQuery(new MatchAllDocsQuery(), entityType).getResultSize();
   }

   private static final class LuceneIndexTracker {

      private final File indexBase;
      private long indexVersion;

      public LuceneIndexTracker(File indexBase) {
         this.indexBase = indexBase;
      }

      public void mark() {
         indexVersion = getLuceneIndexVersion();
      }

      public boolean indexChanged() {
         return getLuceneIndexVersion() != indexVersion;
      }

      private long getLuceneIndexVersion() {
         return indexBase.list() == null ? -1 : SegmentInfos.getLastCommitGeneration(indexBase.list());
      }
   }

   @Listener
   @SuppressWarnings("unused")
   private static final class CacheListener {

      private final LongAdder passivationCount = new LongAdder();
      private final LongAdder activationCount = new LongAdder();

      @CacheEntryPassivated
      public void onEvent(CacheEntryPassivatedEvent payload) {
         if (!payload.isPre())
            passivationCount.increment();
      }

      @CacheEntryActivated
      public void onEvent(CacheEntryActivatedEvent payload) {
         if (!payload.isPre())
            activationCount.increment();
      }

      public int numberOfPassivations() {
         return passivationCount.intValue();
      }

      public int numberOfActivations() {
         return activationCount.intValue();
      }
   }
}
