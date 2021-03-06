package org.infinispan.query.impl.externalizers;

/**
 * Identifiers used by the Marshaller to delegate to specialized Externalizers.
 * For details, read https://infinispan.org/docs/10.0.x/user_guide/user_guide.html#preassigned_externalizer_id_ranges
 *
 * The range reserved for the Infinispan Query module is from 1600 to 1699.
 *
 * @author Sanne Grinovero
 * @since 7.0
 */
public interface ExternalizerIds {

   Integer LUCENE_QUERY_BOOLEAN = 1601;

   Integer LUCENE_QUERY_TERM = 1602;

   Integer LUCENE_TERM = 1603;

   Integer LUCENE_SORT = 1604;

   Integer LUCENE_SORT_FIELD = 1605;

   Integer LUCENE_TOPDOCS = 1606;

   Integer CLUSTERED_QUERY_TOPDOCS = 1607;

   Integer LUCENE_SCORE_DOC = 1608;

   Integer LUCENE_TOPFIELDDOCS = 1609;

   Integer LUCENE_FIELD_SCORE_DOC = 1610;

   Integer LUCENE_QUERY_MATCH_ALL = 1612;

   Integer INDEX_WORKER = 1613;

   Integer LUCENE_BYTES_REF = 1615;

   Integer LUCENE_QUERY_PREFIX = 1618;

   Integer LUCENE_QUERY_WILDCARD = 1619;

   Integer LUCENE_QUERY_FUZZY = 1620;

   Integer QUERY_DEFINITION = 1621;

   Integer CLUSTERED_QUERY_COMMAND_RESPONSE = 1622;
}
