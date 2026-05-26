import {
  DocumentState,
  LifecycleAction,
  invoiceTransitions,
  receiptTransitions,
  isAllowed,
  nextState,
} from './eta-states';

describe('eta-states generated constants', () => {
  it('DocumentState has all 7 states', () => {
    const values = Object.values(DocumentState);
    expect(values.length).toBe(7);
    expect(values).toContain('DRAFT');
    expect(values).toContain('SUBMITTING');
    expect(values).toContain('SUBMITTED');
    expect(values).toContain('IN_REVIEW');
    expect(values).toContain('ACCEPTED');
    expect(values).toContain('REJECTED');
    expect(values).toContain('CANCELLED');
  });

  it('LifecycleAction has all actions', () => {
    const values = Object.values(LifecycleAction);
    expect(values.length).toBe(12);
  });

  it('invoiceTransitions has entries for all 7 states', () => {
    expect(Object.keys(invoiceTransitions).length).toBe(7);
  });

  it('receiptTransitions has entries for all 7 states', () => {
    expect(Object.keys(receiptTransitions).length).toBe(7);
  });

  it('isAllowed returns true for DRAFT+EDIT using invoice map', () => {
    expect(isAllowed('DRAFT', 'EDIT')).toBe(true);
  });

  it('isAllowed returns true for DRAFT+EDIT using receipt map', () => {
    expect(isAllowed('DRAFT', 'EDIT', receiptTransitions)).toBe(true);
  });

  it('isAllowed returns false for REJECTED+EDIT', () => {
    expect(isAllowed('REJECTED', 'EDIT')).toBe(false);
  });

  it('isAllowed returns false for CANCELLED+any', () => {
    const actions = Object.values(LifecycleAction);
    for (const action of actions) {
      expect(isAllowed('CANCELLED', action)).toBe(false);
    }
  });

  it('isAllowed returns false for REJECTED for all actions', () => {
    const actions = Object.values(LifecycleAction);
    for (const action of actions) {
      expect(isAllowed('REJECTED', action)).toBe(false,
          'REJECTED should not allow ' + action);
    }
  });

  it('nextState returns correct target for DRAFT+SUBMIT', () => {
    expect(nextState('DRAFT', 'SUBMIT')).toBe('SUBMITTING');
  });

  it('nextState returns null for DRAFT+DELETE', () => {
    expect(nextState('DRAFT', 'DELETE')).toBeNull();
  });

  it('nextState throws for invalid transition', () => {
    expect(() => nextState('CANCELLED', 'EDIT')).toThrow();
  });

  it('receipt nextState works with explicit map', () => {
    expect(nextState('DRAFT', 'SUBMIT', receiptTransitions))
        .toBe('SUBMITTING');
  });
});
