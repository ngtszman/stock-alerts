package ar.com.sac.services;

import ar.com.sac.model.IStockWrapper;
import ar.com.sac.model.Quote;
import ar.com.sac.model.StockWrapper;
import ar.com.sac.services.dao.QuoteDAO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yahoofinance.Stock;
import yahoofinance.histquotes.HistoricalQuote;

@Service
@EnableScheduling
@PropertySource("classpath:application.properties")
@Transactional
public class StockService implements IStockService{
   
   @Autowired
   private QuoteDAO quoteDAO;
   
   @Autowired
   private YahooFinanceService yahooFinanceService;
   
   @Value("${stocks.db.enable}")
   private Boolean usingDB;

   public IStockWrapper getStock( String symbol ) throws IOException{
      return new StockWrapper( yahooFinanceService.getStock( normalizeSymbol( symbol ) ) );
   }
   
   public Map<String,IStockWrapper> getStocks( String[] symbols ) throws IOException{
      normalizeSymbols( symbols );
      Map<String,IStockWrapper> resultMap = new HashMap<>();
      Map<String, Stock> yahooMap = yahooFinanceService.getStocks( symbols );
      for(String symbol :  yahooMap.keySet()){
         resultMap.put( symbol, new StockWrapper( yahooMap.get( symbol ) ) );
      }
      return resultMap;
   }
   
   /**
    * Return a year of quotes from a symbol
    */
   public List<Quote> getHistory( String symbol ) throws IOException{
      Calendar yearAgo = Calendar.getInstance();
      yearAgo.add( Calendar.YEAR, -1 );
      return getHistory( symbol, yearAgo, Calendar.getInstance() );
   }
   
   public List<Quote> getHistory( String symbol, Calendar from, Calendar to ) throws IOException{
      return getHistory( new String[]{ normalizeSymbol( symbol )}, from, to ).get( symbol );
   }
   
   public  Map<String, List<Quote>> getHistory( String[] symbols, Calendar from, Calendar to ) throws IOException{
      normalizeSymbols( symbols );
      Map<String, List<Quote>> resultMap = new HashMap<>();
      if( usingDB ){
         resultMap = quoteDAO.findByRangeInBulk( symbols, from, to );
         updateMissingQuotes( symbols, from, to, resultMap );
      }else{
         fillHistoryFromYahooService( symbols, from, to, resultMap );
      }
      
      return resultMap;
   }

   private void fillHistoryFromYahooService( String[] symbols, Calendar from, Calendar to,
                                             Map<String, List<Quote>> resultMap ) throws IOException {
      Map<String, List<HistoricalQuote>> historyMap = yahooFinanceService.getHistory( symbols, from, to );
      for( String symbol : historyMap.keySet() ){
         resultMap.put( symbol, historyToQuotes( historyMap.get( symbol ) ));
      }
      addLastQuote( symbols, resultMap );
   }

   private Map<String, List<Quote>> autoLoadDB( String[] symbols, Calendar from, Calendar to ) {
      Map<String, List<Quote>> resultMap = new HashMap<>();
      try {
         fillHistoryFromYahooService( symbols, from, to, resultMap );
         for( String symbol : resultMap.keySet() ){
            importQuotes( resultMap.get( symbol ) );
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
      return quoteDAO.findByRangeInBulk( symbols, from, to );
   }

   private void updateMissingQuotes( String[] symbols, Calendar from, Calendar to, Map<String, List<Quote>> resultMap ) {
      List<String> missedSymbols = new ArrayList<>();
      int size;
      for( String symbol : symbols ){
         size = resultMap.get( symbol ) == null ? 0 : resultMap.get( symbol ).size();
         if ( size == 0 ){
            missedSymbols.add( symbol );
         }
      }
      
      if( missedSymbols.size() > 0 ){
         Map<String, List<Quote>> missedMap = autoLoadDB( missedSymbols.toArray(new String[missedSymbols.size()]), from, to );
         resultMap.putAll( missedMap );
      }
      
   }

   private void addLastQuote( String[] symbols, Map<String, List<Quote>> resultMap ) throws IOException {
      Map<String, IStockWrapper> stocksMap = getStocks( normalizeSymbols( symbols ) );
      Quote lastQuote, todayQuote;
      for(String symbol : stocksMap.keySet()){
         todayQuote = stocksMap.get( symbol ).getLastQuote();
         List<Quote> list = resultMap.get(symbol) != null ? resultMap.get(symbol) : resultMap.put( symbol, new ArrayList<Quote>() );
         lastQuote = list.get(0);
         if(lastQuote == null || todayQuote.getDate().get( Calendar.DATE ) != lastQuote.getDate().get( Calendar.DATE )){
            list.add( todayQuote );
         }
      }
   }

   private List<Quote> historyToQuotes( List<HistoricalQuote> history ) {
      List<Quote> quotes = new ArrayList<Quote>( history.size() );
      Quote newQuote;
      for(HistoricalQuote h : history){
         newQuote = new Quote(h);
         //Only takes in account positive (valid) close prices
         if( newQuote.getClose().doubleValue() >= 0D ){
            quotes.add( newQuote );
         }
      }
      return quotes;
   }
   
   @Transactional
   public void importQuotes(Collection<Quote> quotes){
      for(Quote quote : quotes){
         quoteDAO.update( quote );
      }
   }
   
   public List<String> getSymbols(){
      return quoteDAO.getLoadedSymbols();
   }
   
   /**
    * Update DB with last quote of every ticker loaded
    */
   @Scheduled(cron = "${stocks.dailyUpdateDB.cron}")
   @Transactional
   public void updateDBJob(){
      System.out.println( "Update DataBase JOB: " + new Date() );
      List<String> symbols = getSymbols();
      try {
         Map<String, IStockWrapper> map = getStocks( symbols.toArray( new String[symbols.size()] ) );
         for(IStockWrapper stock : map.values()){
            quoteDAO.update( stock.getLastQuote() );
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   @Override
   @Transactional
   public void deleteStock( String symbol ) {
      quoteDAO.removeQuotes( normalizeSymbol( symbol ) );
   }
   
   private String normalizeSymbol(String symbol){
      return symbol.toUpperCase().trim();
   }
   
   private String[] normalizeSymbols(String[] symbols){
      for(int i = 0; i < symbols.length; i++){
         symbols[i] = normalizeSymbol( symbols[i] );
      }
      return symbols;
   }

   @Override
   @Transactional
   public void autoUpdateDBHistory( Integer year ) {
      if(year == null){
         return;
      }
      Calendar from = Calendar.getInstance();
      from.set( Calendar.YEAR, year );
      from.set( Calendar.MONTH, 0 );
      from.set( Calendar.DATE, 1 );
      List<String> symbols = getSymbols();
      autoLoadDB( symbols.toArray( new String[symbols.size()] ), from, Calendar.getInstance() );
   }
  
   @Override
   @Transactional
   public void updateDBHistory( Integer year, String symbol ) {
      if(year == null || symbol == null){
         return;
      }
      Calendar from = Calendar.getInstance();
      from.set( Calendar.YEAR, year );
      from.set( Calendar.MONTH, 0 );
      from.set( Calendar.DATE, 1 );
      List<HistoricalQuote> history;
      try {
         history = yahooFinanceService.getHistory( symbol, from, Calendar.getInstance() );
         importQuotes( historyToQuotes( history ) );
      } catch (IOException e) {
         e.printStackTrace();
         throw new RuntimeException( e.getMessage() );
      }
   }
   
}
