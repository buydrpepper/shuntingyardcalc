/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package shuntingyardcalc;

import java.util.Stack;
import java.util.HashMap; 
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Class for all calculator functions, works independently of the GUI.
 * Limitations: Interprets all unrecognized string inputs as constants, which,
 * if not defined, can cause calculation errors. Error checking should be done by
 * the GUI
 *
 * Uses the "Shunting yard" algorithm to parse the input. (Explained in the
 * methods) 
 * - The calculator evaluates (in real time) the most it possibly can without breaking order of operations 
 * -This means that it doesn't have to store a RPN stack because it evaluates it while doing the "shunting yard" algorithm
 * -Because of this, when an error occurs, the calculator can be certain that the previous request caused it, and doesn't allow it. 
 * - When the user wants an answer, the calculator just tries to append closing brackets 
 * and checks for errors, because they are equivalent to the algorithm 
 *
 */
public class Calculator {

	/*
		Rule for numerical values:
		We can append a numerical value only if the number of operations in the stack with 2 inputs are equal to the current number of elements in valStack.  
		(This is checked for each request)
		- Also, This condition must be fufilled before evaluating.
		This means that each operation must have the right number of parameters except one, which will be satisfied by appending a digit.
	 */
	//Enumeraotr for operators. Params: Precidence, is left to right, number of inputs
	private enum Operator {
		OPEN_BRAC(Integer.MAX_VALUE, true, 1),
		/*
		Open bracket "(" is allowed to be appended anywhere. 
		When not next to an operator, or next to a closed bracket, multiplication is implied:
			a(b) = a*(b) or (4)(5) = 4 * 5
		This also means weak grouping is implied, i.e.
			a/b(c) = c*a/b
		This rule translates to pushing a multiplication operation before the operator only if 


		We push open brackets to the stack as usual but no other operator can "pop" brackets out except for closing brackets. (This ensures brackets are prioritized in order of operations)

		Mismatched Brackets:
			- empty brackets "()" are not allowed
			- Missing brackets are fixed automatically
		 */
		CLOSE_BRAC(Integer.MIN_VALUE, false, 0),
		/*
		Closed brackets will cause all operators in the stack to be popped and evaluated, until the first open bracket it sees, which will cause the pair to be removed.
		For valid syntax, the number of operators which need 2 inputs must be equal to one less than the number of variables in varStack, and the varStack needs at least 1 element.
		See Open Bracket for more.
		 */
		DEC(5, false, 2),
		/*
		The generic rule for operators with 2 numInputs are described here:
			Can add to operator stack only if, when added to the operator stack, the number of operators in the stack which take 2 inputs will be equal to the valStack's size. 
			Since we enforce this rule for each request, this rule is enough to ensure the entire request is valid.
			Also, such an operator must not be "trailing" (which is handled by brackets or the final evaluation)

		Decimals are treated as regular operators. 
		However, Decimals in particular may not be consecutive in the operator stack (causing more than 1 decimal per number), and can only be attatcked to a digit, i.e. (3).5 and 3.(5)is invalid
		 */
		ADD(1, false, 2),
		/*
		Follows the generic rule for 2 inputs (see decimal)
		 */
		SUB(1, false, 2),
		/*
			Follows the generic rule for 2 inputs (see decimal)
			Converted to negative sign if a request to append it violates the rule.
		 */
		MULT(2, false, 2),
		/*
			Follows the generic rule for 2 inputs (see decimal)
		 */
		DIV(2, false, 2),
		/*
			Follows the generic rule for 2 inputs (see decimal)
			Division by zero is handled when the attempted valStack reduction fails
		 */
		SIN(4, true, 1),
		/*
		The generic rule for functions:
			They are evaluated left to right if they have the same precidence: a^b^c != (a^b)^c
			We treat order dependent operators as having higher precidence than themselves, so the rightmost operation is always evaluated first

		The generic rule for functions with one input:
			We can append them to the input without restrictions
			They cannot be missing an input value (handled by brackets or the final evaluation)
			When appended after a value, e.g. 2sin(4), interpret as implied brackets, i.e. 2(sin(4)) (See brackets section for details)
		 */
		COS(4, true, 1),
		//Follows the generic rule for functions with one input (see sine)
		TAN(4, true, 1),
		//Follows the generic rule for functions with one input (see sine)
		SQRT(4, true, 1),
		//Follows the generic rule for functions with one input (see sine)
		NEG(5, true, 1),
		/*
		A negative sign is treated as a function with one variable: f(x) = -x.
		therefore it is treated as order dependent and has high precidence because the negative sign is strongly grouped with its value, just like digits are strongly grouped with each other.
		Interpret a "-" as a negation if and only if the request to append "-" would result in a syntax error.
		This works becuase trailing "-" (which is invalid syntax) is valid until the user tries to use another operator, like (5-*4)
		This will be caught while appending the other operator.
		a---b is treated as a-(-(-b))
		 */
		POW(3, true, 2),
		//Follows the generic rule for functions, and the generic rule for operators with 2 inputs (see sine and decimal).
		INVALID(0, false, 0);

		public final int precidence; 
		public final boolean isLeftToRight;
		public final int numInputs;

		/**
		 * Constructor for Operators Sets the precidence, isLeftToRight,
		 * numInputs respectively
		 *
		 * @param p
		 * @param b
		 * @param n
		 */
		private Operator(int p, boolean b, int n) {
			isLeftToRight = b;
			precidence = p;	
			numInputs = n; 
		}
	}

	private boolean recalculateStack;
	private boolean degreeMode; 
	public int precision; //display precision (doesn't affect internal results, so accessable everywhere)

	private final HashMap<String, BigDecimal> variableMap; //map for variables //takes in the string rep. and returns the value.
	private final HashMap<String, Operator> opMap; //map for operators, takes in its string rep. and returns the corresponding op.
	
	
	private Stack<BigDecimal> valStack; //stack for values
	private Stack<Operator> operatorStack; //stack for operators
	private Stack<String> displayStack; //stack for displayed value
	//valSurplus: num available values - operators with 2 inputs. Should be updated when adding values to the stack
	private int valSurplus; 

	/**
	 * Constructor for the calculator, initializes all of its variables
	 *
	 */
	public Calculator() {
		variableMap = new HashMap<>();
		opMap = new HashMap<>(); 
		valStack = new Stack<>(); 
		operatorStack = new Stack<>(); 
		displayStack = new Stack<>(); 
		recalculateStack = false; 
		precision = 10; 
		degreeMode = false; 
		valSurplus = 0; 

		opMap.put("+", Operator.ADD);
		opMap.put("(", Operator.OPEN_BRAC);
		opMap.put(")", Operator.CLOSE_BRAC);
		opMap.put(".", Operator.DEC);
		opMap.put("-", Operator.SUB);
		opMap.put("*", Operator.MULT); 
		opMap.put("/", Operator.DIV); 
		opMap.put("sin", Operator.SIN); 
		opMap.put("cos", Operator.COS);
		opMap.put("tan", Operator.TAN); 
		opMap.put("sqrt", Operator.SQRT); 
		opMap.put("neg", Operator.NEG); 
		opMap.put("^", Operator.POW);

		variableMap.put("Ans", BigDecimal.valueOf(0)); 
		variableMap.put("π", BigDecimal.valueOf(Math.PI)); 
		variableMap.put("e", BigDecimal.valueOf(Math.E));
	}


	/** Gets the degreemode value
	 * pre/post conditions: none
	 * @return the value of degreeMode
	 */
	public boolean getDegreeMode() {
		return degreeMode; 
	}

	/**
	 * Gets the display string 
	 * preconditions: displayStack has no errors
	 * postconditions: state of the calculator is unaltered
	 * @return Returns the display string to be shown to the user
	 */
	public String getDisplayString() {
		String displayString = "";
		for (String s : displayStack) {
			displayString += s;
		}
		return displayString;
	}

	/**
	 * Toggles the angle measure (rad/deg) 
	 * preconditions: none 
	 * postconditions: recalculateStack must be true, otherwise previous
	 * trigonometric functions will still be in the previous mode
	 */
	public void toggleAngleMeasure() {
		recalculateStack = true; 
		degreeMode = !degreeMode; 
	}

	/**
	 * Applies an operation of an operator to a stack of values. Pops op.numInputs values from the stack and applies it based on bottom->top order
	 * Precondition: vals must have at least op.numInputs elements, vals must be a temporary stack and not affect internal state.
	 * 				-op should be a valid operator, and not op.INVALID
	 * 				-decimal operator should be allowed to act as addition, as the decimal place should be taken care of prior.
	 * Postcondition: No side effects other than to vals
	 * @param op The operator
	 * @param vals The stack of values
	 * @return Returns an error code: 
	 * 3: unrecognized op. (should never happen)
	 * 2: Math error
	 * 1: Undefined
	 */
	private int applyOperation(Operator op, Stack<BigDecimal> vals) {
		BigDecimal first, second, ret; //declare variables for operands and result
		double dbl; //declare variable for double precision operations
		BigDecimal fullCircle = degreeMode ? BigDecimal.valueOf(360) : BigDecimal.valueOf(Math.PI * 2);
		BigDecimal halfCircle = fullCircle.divide(BigDecimal.valueOf(2)); 

		//internal precision should be indep. of display precision, arbitrarily set to 1337
		MathContext mc = new MathContext(1337, RoundingMode.HALF_EVEN); 
		switch (op) {
			case MULT: 
				first = vals.pop(); 
				second = vals.pop(); 
				ret = first.multiply(second);
				if (ret.compareTo(new BigDecimal("1e99999")) > 0) {
					return 2; //return error code for overflow
				}
				vals.push(ret); 
				break;
			case DIV:
				first = vals.pop(); 
				second = vals.pop(); 
				try {
					ret = second.divide(first, mc); 
				} catch (ArithmeticException ex) {
					return 1; //return error code for division by zero (Undef.)
				}
				if (ret.compareTo(new BigDecimal("1e99999")) > 0) {
					return 2; //return error code for overflow (Math err.)
				}
				vals.push(ret); 
				break;
			case DEC: //decimals are treated as addition because the "0." is already accounted for in appendOperator
			case ADD: 
				vals.push(vals.pop().add(vals.pop())); 
				break;//breaks;
			case SUB: //(we negate because the order is bottom->top, and we subtracted in top->bottom order)
				vals.push(vals.pop().subtract(vals.pop()).negate());
				break;
			case NEG: 
				vals.push(vals.pop().negate());
				break;
			case POW:
				first = vals.pop(); 
				second = vals.pop();
				if (second.compareTo(new BigDecimal("1e5000")) > 0) {
					return 2; //return error code for overflow(Math err.)
				}
				if (second.toString().length() * first.longValue() > 99999) {
					return 2; //return error code for overflow(Math err.)
				}
				if (first.compareTo(BigDecimal.ZERO) >= 0 && first.stripTrailingZeros().scale() <= 0) {
					if (second.compareTo(BigDecimal.ZERO) == 0 && first.compareTo(BigDecimal.ZERO) == 0) {
						return 1; //return error code for 0^0 (undef.)
					}
					try {
						//bigdecimal only supports exponentiation with pos. integer powers
						ret = second.pow(first.intValueExact()); //calculate power with integer exponent
					} catch (ArithmeticException ex) { 
						return 2; //return error code overflow (Math err.)
					}
				} else { //if exponent is not a non-negative integer (bigdecimal can't use it)
					dbl = Math.pow(second.doubleValue(), first.doubleValue());
					if (!Double.isFinite(dbl)) {
						return 2; //return error code for overflow (Math err.)
					}
					ret = new BigDecimal(dbl);
				}
				vals.push(ret); 
				break;
			case SQRT:
				if (vals.peek().compareTo(BigDecimal.ZERO) < 0) {
					return 2; //return error code for square root of negative number (Math error)
				}
				vals.push(vals.pop().sqrt(mc));
				break;
			case SIN: {
				//for trig. operators, rememeber parity: sine, tan -> odd, cos -> even, and sin,cos,tan are periodic for a full circle.
				//Then we can ignore the signs and take the angle % halfCircle, 
				//by symmetry, calculate the unsigned value (more accurate since java.Math class is double precision only), and put the signs later.
				//This means that some operations like sine(999999999999pi/2) are much more accurate
				first = vals.pop();
				boolean negate = first.compareTo(BigDecimal.ZERO) < 0; //negate if operand is negative (odd parity)
				if (negate) {
					first = first.negate(); 
				}
				first = first.remainder(fullCircle); //get the equivalent angle within full circle
				if (first.compareTo(halfCircle) > 0) { //if angle is in the second half of the circle
					negate = !negate;
				}
				dbl = first.remainder(halfCircle).doubleValue();
				if (degreeMode) {
					dbl *= Math.PI / 180; //convert angle to radians
				}
				ret = new BigDecimal(Math.sin(dbl));
				if (negate) {
					ret = ret.negate();
				}
				vals.push(ret);
				break;
			}
			case COS: { //cosine operator, exactly the same as sine, but shifted because sine(90-a) = cos(a)
				first = halfCircle.divide(BigDecimal.valueOf(2)).subtract(vals.pop()); //shift the angle for cosine
				boolean negate = first.compareTo(BigDecimal.ZERO) < 0; //negate if operand is negative (odd parity)
				if (negate) { 
					first = first.negate();
				}
				first = first.remainder(fullCircle); //get the equivalent angle within full circle
				if (first.compareTo(halfCircle) > 0) { //if angle is in the second half of the circle
					negate = !negate; 
				}
				dbl = first.remainder(halfCircle).doubleValue();
				if (degreeMode) {
					dbl *= Math.PI / 180; //convert angle to radians
				}
				ret = new BigDecimal(Math.sin(dbl));
				if (negate) { 
					ret = ret.negate();
				}
				vals.push(ret);
				break;
			}
			case TAN: {
				//odd parity reduction:
				first = vals.pop();
				boolean negate = first.compareTo(BigDecimal.ZERO) < 0; //negate if operand is negative (odd parity)
				if (negate) {
					first = first.negate();
				}
				//unlike sine, tangent can be treated as periodic on a half circle, so we can just take the modulo
				dbl = first.remainder(halfCircle).doubleValue();
				//tan calculation begins:
				if (degreeMode) { 
					if (dbl == 90) { //tried tangent of an undefined value (remember we reduced the angle to a half circle)
						return 1; //return error code (Undef. error)
					}
					dbl *= Math.PI / 180; //convert angle to radians
				} else if (dbl == Math.PI / 2) {// undef. value (remember we reduced the angle to a half circle)
					return 1; //return error code (Undef. error)
				}

				ret = new BigDecimal(Math.tan(dbl));
				if (negate) {
					ret = ret.negate();
				}
				vals.push(ret); 
				break;
			}
			default: //if unrecognized operator (should never happen)
				return 3;
		}
		return 0;
	}

	/**
	 * Gets the prefix of the operator and check current operator for correctness, necessary for correct evaluation.
	 * Precondition: all member variables properly updated and valid, operator is a valid one
	 * Post ocndition: no side effects
	 * @param op the operator
	 * @return the necessary prefix for the operator, or null if none. Ex:left bracket has a prefix of multiplication.
	 * 			On syntax error for the current operator: Return Operator.INVALID
	 */
	Operator getPrefix(Operator op) {
		//the rules here are arbitrary, but ensure a good format for the calculator, see Operator enum for details
		switch (op) {
			case DEC:
				if (displayStack.isEmpty()) {
					return Operator.INVALID;
				}
				if (!displayStack.peek().matches("^[0-9]+$")) {
					return Operator.INVALID;
				}
				if (!operatorStack.isEmpty() && operatorStack.peek() == op) {
					return Operator.INVALID;
				}
				break;
			case CLOSE_BRAC:
				if (displayStack.isEmpty()) {
					return Operator.INVALID;
				}
				if (valSurplus == 0 || displayStack.peek().equals("(")) {
					return Operator.INVALID;
				}
				break;
			default: 
				break;
		}
		switch (op.numInputs) {
			case 1:
				if (valSurplus == 1) {
					return getPrefix(Operator.MULT) == null ? Operator.MULT : Operator.INVALID;
				} else if (!operatorStack.empty() && operatorStack.peek() == Operator.DEC) {
					return Operator.INVALID;
				}
				break;
			case 2:
				if (valSurplus == 0) {
					return Operator.INVALID;
				}
				break;
			default:
				break; 
		}
		return null;
	}

	/**
	 * appends the operator to the operatorStack, and partially evaluates the stack if possible. Uses the shunting yard algorithm to do this
	 * 
	 * Precondition: There are no syntax / evaluation errors in the previous operatorStack, valStack, displaystack
	 * 		-valStack is not up to date if the user is building a number
	 * i.e. member variables are valid
	 * 
	 * Postcondition: There are no errors in formatting of the two stacks.
	 * The two stacks are altered if and only if there was no error valSurplus is properly updated
	 * i.e. member variables stay valid
	 *
	 * @param op the operator to append
	 * @return the error / success code: 
	 * -1: syntax error 
	 * 0: Success 
	 * 1:Undefined 
	 * 2: Math error
	 */
	private int appendOperator(Operator op) {
		var prefix = getPrefix(op);
		if (prefix == Operator.INVALID) {
			//subtraction should always be subtraction unless it doesn't work, then it's negation:
			if (op == Operator.SUB && getPrefix(Operator.NEG) == null) { 
				op = Operator.NEG; 
				prefix = null;
			} else {
				return -1;
			}
		}
		//ensure that the currently valid member variables stay valid by working on a copy
		var tmpOpStack = (Stack<Operator>) operatorStack.clone(); 
		var tmpValStack = (Stack<BigDecimal>) valStack.clone();

		//Push user inputted numbers to the tempvalstack, since they aren't pushed to valstack until confirmed to be finished building
		if (!displayStack.empty() && displayStack.peek().matches("^[0-9]+$")) { 
			String toPush = displayStack.peek();
			if (!tmpOpStack.isEmpty() && tmpOpStack.peek() == Operator.DEC) {
				//The decimal apply operation function is just addition with more precidence, because we scale it appropriately here
				toPush = "0." + toPush;
			}
			tmpValStack.push(new BigDecimal(toPush)); 
		}
		//Shunting yard part of the function, operators "knock" operators off of the stack and cause them to be applied to the 
		//value stack, if the current operator is of lower precidence. 
		//If an operator has n inputs, then n values from the stack will be replaced with just one resulting value
		boolean isPrefix = prefix != null;
		var cur = isPrefix ? prefix : op;
		while (true) {
			if (cur == null || tmpOpStack.empty()) {
				if (!isPrefix) break;
				else {
					isPrefix = false; 
					tmpOpStack.push(prefix); //push prefix to operator stack (it is confirmed valid)
					cur = op; 
				}
			}
			var top = tmpOpStack.peek(); 
			if (top == Operator.OPEN_BRAC) {
				//open brackets can't be "knocked off" unless by closing bracket
				if (cur == Operator.CLOSE_BRAC) { 
					tmpOpStack.pop(); 
					//prevent confusing the user, like realizing sqrt(-1) is invalid only after another request is performed
					if (!tmpOpStack.empty() && tmpOpStack.peek().isLeftToRight && tmpOpStack.peek() != Operator.OPEN_BRAC) { 
						int errcode = applyOperation(tmpOpStack.pop(), tmpValStack); 
						if (errcode != 0) { 
							return errcode; 
						}
					}
				}
				cur = null; 
			} else if (cur.precidence <= top.precidence && (!cur.isLeftToRight)) { 
				//we can pop the operator and evaluate it without breaking order of operations
				int errcode = applyOperation(top, tmpValStack); 
				if (errcode != 0) {
					return errcode; 
				}
				tmpOpStack.pop(); 
			} else { 
				cur = null;
			}
		}
		//calculation complete and valid, now update state variables
		if (prefix != null && prefix != Operator.CLOSE_BRAC) {
			valSurplus -= prefix.numInputs - 1; 
		}
		if (op != Operator.CLOSE_BRAC) { 
			valSurplus -= op.numInputs - 1;
			tmpOpStack.push(op); 
		}
		operatorStack = tmpOpStack; 
		valStack = tmpValStack; 
		return 0;
	}

	/** Appends a constant and check for errors
	 *precondition:
	 * 		the constant must be a variable and not a number, like pi
	 * 		all member variables are valid and properly checked
	 * postcondition:
	 * 		all member variables are valid
	 * @param val
	 * @return ttrue if the operation completed, false otherwise
	 */
	private boolean appendConstant(BigDecimal val) {
		if (!displayStack.empty() && displayStack.peek().equals(".")) {
			return false;
		} else if (valSurplus != 0 && appendOperator(Operator.MULT) != 0) {
			//check if there is surplus value and attempt to append multiplication operator: implied multiplication
			return false;
		}
		valStack.push(val); 
		valSurplus++; 
		return true; 
	}

	/**Appends a numerical value (ex: 121) and checks for errors
	 * precondition:
	 * 		all member variables are valid and properly checked
	 * 		Only called by the system (like a macro), not the GUI, because this appedns the entire number rather than a digit
	 * postcondition:
	 * 		all member variables stay valid
	 * 		
	 * @param val value to append
	 * @return true if the operation completed, false otherwise
	 */
	private boolean appendNumerical(String val) {
		//check if the valsurplus is not zero and implied multiplication fails,
		if (valSurplus != 0 && appendOperator(Operator.MULT) != 0) {
			return false;
		}
		valSurplus++; 
		return true; 
	}

	/**Appends a digit and checks for errors
	 * precondition:
	 * 		all member variables are valid and properly checked
	 * postcondition:
	 * 		all member variables are valid
	 * @param digit the digit requested
	 * @return true if the operation completed, false otherwise
	 */
	private boolean appendDigit(String digit) {
		if (!displayStack.empty()) {
			if (displayStack.peek().matches("^[0-9]+$")) { 
				String val = displayStack.pop(); 
				//since a leading zero can be meaningless:
				val = val.equals("0") && (operatorStack.empty() || operatorStack.peek() != Operator.DEC) ? digit : val + digit; 
				displayStack.push(val); 
				return true;
			} else if (valSurplus != 0 && appendOperator(Operator.MULT) != 0) {
				// if there are more numbers than operable, the user probably implies multiplication, ex: 3sin(a) = 3*sin(a)
				//check if there is surplus value and attempt to append multiplication operator
				return false; 
			}
		}
		valSurplus++;
		displayStack.push(digit); 
		return true; 
	}

	/** Clears the state of the calculator stacks, and the variables which depend on them
	 * Precondition: none
	 * Postcondition: all stacks are cleared and all variables in a valid state
	 *
	 */
	private void clearState() {
		displayStack.clear();
		valStack.clear(); 
		valSurplus = 0; 
		operatorStack.clear();
		recalculateStack = false; 
	}

	/** appends a command and updates member variable states accordingly
	 * precondition:
	 *  	all member variable states are valid
	 * 	the request is valid (matches a valid case in append method)
	 * postcondition:
	 * 	all member variable states stay valid
	 * @param s the command
	 * @return the error code, or null if no error
	 */
	private String append(String s) {
		String msg = null; 
                // flag if the user wants to subtract from their answer (vs. default behaviour to append a "neg")
		boolean subtractAns = !recalculateStack && displayStack.empty() && !(variableMap.get("Ans").compareTo(BigDecimal.ZERO) == 0); 
		if (recalculateStack) { 
			var displayStackCpy = (Stack<String>) displayStack.clone(); 
			clearState(); 
			for (String token : displayStackCpy) { 
				append(token); 
			}
		}
		if (s.matches("^[0-9]+$")) { // if token is a number
			if (s.length() > 1) { // if length of token is greater than 1
				if (!appendNumerical(s)) {
					msg = "Invalid Expression!"; 
				}
			} else { // if length of token is 1
				if (!appendDigit(s)) {
					msg = "Invalid Expression!";
				}
				return msg; 
			}
		} else if (variableMap.containsKey(s)) { 
			if (!appendConstant(variableMap.get(s))) {
				msg = "Invalid Expression!"; 
			}
		} else if (opMap.containsKey(s)) { 
			var op = opMap.get(s); 
			if (op == Operator.SUB && subtractAns) { 
				append("Ans");
			}
			int errcode = appendOperator(op); 
			switch (errcode) { 
				case -1:
					msg = "Syntax Error!"; 
					break; 
				case 1:
					msg = "Undefined Result!";
					break;
				case 2: 
					msg = "Math Error!";
					break;
				default: 
					break;
			}
		}

		if (msg == null) {
			displayStack.push(s); 
		} else if (displayStack.empty()) { 
			append("Ans"); 
			msg = append(s); 
			if (msg != null) { 
				clearState();
			}
		}

		return msg; 
	}

	/**Request an append command to the calculator and returns an error code
	 * precondition:
	 * 	all member variable states are valid
	 * 	the request is valid (matches a valid case in append method)
	 * postcondition:
	 * 	all member variable states stay valid
	 * 
	 * @param req the command
	 * @return the error message, or null if no error
	 */
	public String requestAppend(String req) {
		return append(req); 
	}
	/**Gets the evaluated result of the expression
	 * preconditions:
	 * 	all member variables and their states are valid
	 * 	
	 * post conditions:
	 * 	all member variables and their state are valid
	 * 
	 * @return an array of 2 strings:
	 * 	[0]: the expression, or an empty string if an error occured
	 * 	[1]: the evaluated value, or the error message if an error occured
	 */
	public String[] getEvaluation() {
		int bracv = 0; 
		for (String s : displayStack) {
			if (s.equals("(")) {
				bracv++;
			} else if (s.equals(")")) {
				bracv--; 
			}
		}
		for (int i = 0; i < bracv; i++) {
			String msg = append(")"); 
			if (msg != null) { 
				return new String[]{"", msg};
			}
		}
		//For the evaluation, the calculator just appends one more closing bracket than neccessary, ensures that
		//all operators are applied
		String msg = append(")"); 
		if (msg != null) { 
			return new String[]{"", msg}; 
		}
		if (valStack.size() > 1) {
			return new String[]{"", "An Internal error occured!"}; 
		}
		displayStack.pop(); 
		for (int i = 0; i < -bracv; i++) { 
			displayStack.insertElementAt("(", 0); 
		}
		if (bracv < 0) { 
			bracv = -bracv; 
		}

		// remove unnecessary brackets from display stack
		while (displayStack.elementAt(0).equals("(") && displayStack.peek().equals(")") && bracv > 0) {
			bracv--; 
			displayStack.pop(); 
			displayStack.remove(0); 
		}

		BigDecimal res = valStack.peek();
		variableMap.put("Ans", res);
		//prepare output array with display string and rounded result
		String[] output = {getDisplayString(), res.round(new MathContext(precision, RoundingMode.HALF_EVEN)).toString()}; 
		clearState();
		return output;
	}

	/**Request a pop from the calculator
	 * preconditions:
	 * 	member variables have valid states
	 * postconditions:
	 * 	member variables have valid states (recalculateStack must be true)
	 */
	public void requestPop() {
		if (displayStack.empty()) { 
			return;
		}
		recalculateStack = true;
		displayStack.pop();
	}

	/** requests the clear through the clear command
	 * preconditions: none
	 * post conditions: member variables have valid states
	 */
	public void requestClearBtn() {
		clearState();
		variableMap.put("Ans", BigDecimal.ZERO);
	}

}
