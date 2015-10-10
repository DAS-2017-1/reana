package Parsing.SequenceDiagrams;

import Parsing.Node;

/**
 * Set of static methods to deal with I/O for SDs
 *
 */
public class SDUtil {
	/**
	 * For DEBUG purposes only.
	 * Prints to STDOUT the information of a SD Fragment  
	 * @param f a MagicDraw UML SD fragment
	 */
	public static void printFragment(Fragment f) {
		if (f.getName() != null && !f.getName().isEmpty()) {
			System.out.println("[Fragment = " + f.getName() + "]");
		} else {
			System.out.println("[Fragment = " + ((Operand) f.getNodes().get(0)).getGuard() + "]");
		}
	}
	
	/**
	 * For DEBUG purposes only.
	 * Prints to STDOUT the information of a SD Message  
	 * @param m a MagicDraw UML SD message
	 */
	public static void printMessage(Message m) {
		System.out.println("[" + m.getName() + "]: "
				+ m.getSender().getName() + "->"
				+ m.getReceiver().getName() + " (" + m.getType()
				+ ") p = " + m.getProb() + "; egy: " + m.getEnergy() + "; ex: "
				+ m.getExecTime());
	}
	
	/**
	 * For DEBUG purposes only.
	 * Prints to STDOUT the information of a SD Fragment
	 * @param sd the Magic Draw UML Sequence Diagram
	 * @param indent the indentation being applied to the output
	 */
	public static void printInSequence(Fragment fragment, String indent) {
		
		System.out.print(indent);
		System.out.println("Lifelines: ");
		for (Lifeline l: fragment.getLifelines()) {
			System.out.print(indent);
			System.out.println(l.getName());
		}
		System.out.println();
		
		System.out.print(indent);
		System.out.println("Nodes: ");
		for (Node n: fragment.getNodes()) {
			System.out.print(indent);
			if (n.getClass().equals(Fragment.class)) {
				Fragment f = (Fragment)n;
				printFragment(f);
				printInSequence(f, indent+"\t");
			} else if (n.getClass().equals(Operand.class)) {
				Operand o = (Operand)n;
				System.out.println("Guard = " + o.getGuard());
				for (Node n1: o.getNodes()) {
					System.out.print(indent);
					if (n1.getClass().equals(Message.class)) {
						printMessage((Message)n1);
					} else if (n1.getClass().equals(Fragment.class)) {
						Fragment f = (Fragment)n1;
						printFragment(f);
						printInSequence((Fragment)n1, indent+'\t');
					}
				}
			} else if (n.getClass().equals(Message.class)) {
				printMessage((Message)n);
			}
		}
		System.out.println();
	}
	
	/**
	 * For DEBUG purposes only.
	 * Prints to STDOUT the important information of a SDReader instance, 
	 * following a format that facilitates DEBUGGING
	 * @param sdr
	 */
	public static void printAll(SDReader sdr) {
		System.out.print("Sequence Diagram "+ (sdr.getIndex() + 1) + ": " + sdr.getSD().getName() + "\n\n");
		printInSequence(sdr.getSD(), "");
	} 
	
}