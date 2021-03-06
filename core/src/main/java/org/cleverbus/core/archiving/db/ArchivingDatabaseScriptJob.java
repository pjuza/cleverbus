package org.cleverbus.core.archiving.db;

import org.cleverbus.api.archiving.ProcessArchivingDataJob;
import org.cleverbus.common.log.Log;
import org.cleverbus.core.archiving.ProcessArchivingDataRoute;
import org.cleverbus.core.common.dao.DbConst;
import org.springframework.beans.factory.annotation.Value;

import javax.persistence.*;
import java.util.concurrent.TimeUnit;

/**
 * Job that archiving data into database by calling sql method {@value #ARCHIVE_PROCEDURE_NAME} defined in
 * database script <i>db_schema_postgreSql_archive_0_5.sql</i>.
 * After succesfull archive data will be called procedure {@value #REINDEX_PROCEDURE_NAME} for reindex
 * all tables.
 * All called database method must something return (can not be void).
 *
 * @author Radek Čermák [<a href="mailto:radek.cermak@cleverlance.com">radek.cermak@cleverlance.com</a>]
 * @see ProcessArchivingDataJob
 * @see ProcessArchivingDataRoute
 * @see ArchivingMaxItemsDatabaseScriptJob
 * @since 1.4.15
 */
public class ArchivingDatabaseScriptJob implements ProcessArchivingDataJob {

    /**
     * Name of sql procedure that will be called.
     */
    private static final String ARCHIVE_PROCEDURE_NAME = "archive_records";

    /**
     * Name of sql procedure that reindexed tables.
     */
    private static final String REINDEX_PROCEDURE_NAME = "arch.rebuildIndexes";

    @PersistenceContext(unitName = DbConst.UNIT_NAME)
    private EntityManager em;

    /**
     * Number of days after which the message is to be archived (default value is 7).
     */
    @Value("${archiving.task.archDatabaseScript.archiveOlderInDays:7}")
    private int archiveOlderInDays;

    /**
     * Method will call sql procedure method {@value #ARCHIVE_PROCEDURE_NAME}.
     */
    @Override
    public void startArchivingData() {
        Log.info("Starting archiving data by calling sql procedure '" + ARCHIVE_PROCEDURE_NAME
                + "' older than " + archiveOlderInDays + " days.");

        StoredProcedureQuery procedureArchive = em.createStoredProcedureQuery(ARCHIVE_PROCEDURE_NAME);
        procedureArchive.registerStoredProcedureParameter("archiveOlderInDays", Integer.class, ParameterMode.IN);
        procedureArchive.setParameter("archiveOlderInDays", archiveOlderInDays);

        boolean archiveSuccess;
        try {
            long startTime = System.currentTimeMillis();
            //execute database method
            procedureArchive.execute();
            long duration = System.currentTimeMillis() - startTime;
            String durFormat = String.format("%d min, %d sec", TimeUnit.MILLISECONDS.toMinutes(duration),
                    TimeUnit.MILLISECONDS.toSeconds(duration) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
            Log.info("Archiving data successfully finished in " + durFormat + ".");
            archiveSuccess = true;
        } catch (PersistenceException e) {
            Log.info("Archiving data end with error: " + e.getMessage(), e);
            archiveSuccess = false;
        }

        //if archive is success, we will reindex tables
        if (archiveSuccess) {
            try {
                Log.info("Starting reindex tables by calling sql procedure '" + REINDEX_PROCEDURE_NAME + "'.");
                StoredProcedureQuery procedureReindex = em.createStoredProcedureQuery(REINDEX_PROCEDURE_NAME);
                long startTime = System.currentTimeMillis();
                //execute database procedure to archive data
                procedureReindex.execute();
                long duration = System.currentTimeMillis() - startTime;
                String durFormat = String.format("%d min, %d sec", TimeUnit.MILLISECONDS.toMinutes(duration),
                        TimeUnit.MILLISECONDS.toSeconds(duration) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
                Log.info("Reindex tables successfully finished in " + durFormat + ".");
            } catch (PersistenceException e) {
                Log.info("Reindex tables end with error: " + e.getMessage(), e);
            }
        }
    }
}
