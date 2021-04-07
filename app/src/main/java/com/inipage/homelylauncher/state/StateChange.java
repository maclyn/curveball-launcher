package com.inipage.homelylauncher.state;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class StateChange {
    private final Queue<Transaction> mTransactions;

    private StateChange(Queue<Transaction> transactions) {
        this.mTransactions = transactions;
    }

    public static StateChange immediate(int from, int to) {
        return (
            new StateChange.Builder().maybe(
                Predicate.get(from), Request.immediate(to)).build());
    }

    public static StateChange timed(int from, int to, int duration) {
        return (new StateChange.Builder().maybe(from, to, duration)).build();
    }

    public static StateChange incremental(int from, int to) {
        return (
            new StateChange.Builder().maybe(
                Predicate.get(from), Request.incremental(to)).build());
    }

    public static StateChange increment(int state, float increment) {
        return new StateChange.Builder().maybe(
            Predicate.incremental(state), Request.increment(increment)).build();
    }

    protected Queue<Transaction> getTransactions() {
        return mTransactions;
    }

    public static class Builder {
        private final Queue<Transaction> mStatements;

        public Builder() {
            this.mStatements = new ArrayDeque<>();
        }

        public Builder maybe(Predicate p, Request r) {
            mStatements.add(new Transaction(p, r));
            return this;
        }

        public Builder maybe(List<Predicate> ps, Request r) {
            for (Predicate p : ps) {
                mStatements.add(new Transaction(p, r));
            }
            return this;
        }

        public Builder maybe(int from, int to, int duration) {
            mStatements.add(new Transaction(Predicate.get(from), Request.timed(to, duration)));
            return this;
        }

        public StateChange build() {
            return new StateChange(mStatements);
        }
    }

    protected static class Transaction {
        private final Predicate mPredicate;
        private final Request mRequest;

        private Transaction(Predicate predicate, Request request) {
            this.mPredicate = predicate;
            this.mRequest = request;
        }

        protected Predicate getPredicate() {
            return mPredicate;
        }

        protected Request getRequest() {
            return mRequest;
        }
    }
}
