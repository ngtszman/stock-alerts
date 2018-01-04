package ar.com.sac.services;

import ar.com.sac.model.formulas.FormulaFactory;
import ar.com.sac.model.operations.DummyOperator;
import ar.com.sac.model.operations.OperationConstantValue;
import ar.com.sac.model.operations.OperationFormula;
import ar.com.sac.model.operations.OperationTerm;
import ar.com.sac.model.operations.Operator;
import ar.com.sac.model.operations.OperatorAND;
import ar.com.sac.model.operations.OperatorGREATERThan;
import ar.com.sac.model.operations.OperatorLESSThan;
import ar.com.sac.model.simulator.PositionRecord;
import org.springframework.stereotype.Service;

@Service
public class ExpressionService {
   
   public String normalizeExpression( String expression ){
      return expression.replace( " ", "" ).toUpperCase();
   }
   
   private String replaceSimulationConstants( String expression, Simulation simulation) {
      expression = normalizeExpression(expression);
      expression = expression.replaceAll( "\\[SYMBOL\\]", simulation.getCurrentSymbol() );
      double operationPerfomance = 0d;
      double operationPerfomancePercentage = 0d;
      PositionRecord currentPosition = simulation.getPosition( simulation.getCurrentSymbol() );
      if( currentPosition != null ){
         double sellValue = simulation.getCurrentLastQuote().getClose().doubleValue() * currentPosition.getOrderAmount();
         double sellCommission = simulation.getParameters().getCommissionPercentage() * sellValue / 100d;
         operationPerfomance = (sellValue - currentPosition.getOrderTotalCost()) - sellCommission;
         operationPerfomancePercentage = operationPerfomance * 100d / currentPosition.getOrderTotalCost();
      }
      
      expression = expression.replaceAll( "\\[OPERATION_PERFORMANCE\\]", String.valueOf( operationPerfomance ));
      expression = expression.replaceAll( "\\[OPERATION_PERFORMANCE_PERCENTAGE\\]", String.valueOf( operationPerfomancePercentage ));
      return expression;
   }
   
   public Operator parseSimulatorExpression( String expression, Simulation simulation, IStockService stockService ) {
      expression = replaceSimulationConstants( expression, simulation );
      return parseExpression( expression, stockService );
   }
   
   public Operator parseExpression( String expression, IStockService stockService ) {
      expression = normalizeExpression(expression);
      String[] andSplit = expression.split( "&&" );
      Operator result;
      if(andSplit.length == 1){
         result = processANDTerm( expression, stockService );
      }else{
         OperatorAND and = new OperatorAND();
         for(int i = 0 ; i < andSplit.length; i++){
            and.addTerm( processANDTerm( andSplit[i], stockService ) );
         }
         result = and;
      }
      
      return result;
   }

   private Operator processANDTerm( String expression, IStockService stockService ) {
      Operator operator;
      if(expression.contains( ">" )){
         String[] greaterSplit = expression.split( ">" );
         OperatorGREATERThan greater = new OperatorGREATERThan();
         for(int i = 0 ; i < greaterSplit.length; i++){
            greater.addOperationTerm( processOperationTerm( greaterSplit[i], stockService ) );
         }
         operator = greater;
      }else if(expression.contains( "<" )){
         String[] lessSplit = expression.split( "<" );
         OperatorLESSThan less = new OperatorLESSThan();
         for(int i = 0 ; i < lessSplit.length; i++){
            less.addOperationTerm( processOperationTerm( lessSplit[i], stockService ) );
         }
         operator = less;
      }else{
         //throw new RuntimeException( "Invalid Expression: " + expression );
         return new DummyOperator();
      }
      return operator;
   }

   private OperationTerm processOperationTerm( String expression, IStockService stockService ) {
      OperationTerm result = null;
      int firstParenthesis = expression.indexOf( "(" );
      if (firstParenthesis < 0){ //It's a constant value
         result = new OperationConstantValue( Double.parseDouble( expression ) );
         return result;
      }else{ //It's a formula expression
         result = new OperationFormula( FormulaFactory.getFormula( expression, stockService ) );
      }
      return result;
   }
   
}
