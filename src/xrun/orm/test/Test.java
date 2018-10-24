package xrun.orm.test;

import java.io.File;
import java.util.List;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class Test {

  public static void main(String[] args) throws Exception {
    File f = new File("C:/Users/boyvalenkov/Documents/GitHub/CalcRun/ormdb");
 // this uses h2 but you can change it to match your database
    String databaseUrl = "jdbc:sqlite:" + f.getAbsolutePath().replace('\\', '/');
    // create a connection source to our database
    ConnectionSource connectionSource = new JdbcConnectionSource(databaseUrl);

    // instantiate the DAO to handle Account with String id
    Dao<Account,String> accountDao = DaoManager.createDao(connectionSource, Account.class);

    // if you need to create the 'accounts' table make this call
    TableUtils.createTableIfNotExists(connectionSource, Account.class);

    try {
      List<Account> accounts = accountDao.queryForAll();
      for (Account acc : accounts) {
        System.out.println(acc.getName() + " " + acc.getPassword());
        acc.setPassword("bai hui");
        accountDao.update(acc);
      }
      accounts = accountDao.queryForAll();
      for (Account acc : accounts) {
        System.out.println(acc.getName() + " " + acc.getPassword());
      }
    } finally {
      connectionSource.close();
    }
  }

}
