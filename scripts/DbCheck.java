import java.sql.*;
/** 只读诊断：检查连通性、版本与业务表是否已存在，不执行DDL。 */
class DbCheck {
 public static void main(String[] args) throws Exception {
  String url="jdbc:mysql://"+System.getenv("DB_HOST")+":"+System.getenv("DB_PORT")+"/"+System.getenv("DB_NAME")+"?connectTimeout=5000&socketTimeout=5000&useUnicode=true&characterEncoding=UTF-8";
  try(Connection c=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"))) {
   System.out.println("CONNECTED "+c.getMetaData().getDatabaseProductName()+" "+c.getMetaData().getDatabaseProductVersion());
   try(ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,"t_%",new String[]{"TABLE"})) {while(r.next())System.out.println("EXISTING_TABLE "+r.getString("TABLE_NAME"));}
  }catch(SQLException e){System.err.println("DB_CHECK_FAILED SQLState="+e.getSQLState()+" Code="+e.getErrorCode()+" "+e.getMessage());System.exit(1);}
 }
}
